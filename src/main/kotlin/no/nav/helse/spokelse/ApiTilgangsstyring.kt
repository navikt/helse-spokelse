package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.authorization
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.sykepenger.libs.logging.navngittLogger

private val logger = navngittLogger("no.nav.helse.spokelse.Tilgangsstyring")

internal fun Route.postMedRolle(
    path: String,
    vararg enAvRollene: String,
    dokumentasjon: (RouteConfig.() -> Unit)? = null,
    body: suspend RoutingContext.() -> Unit,
): Route {
    require(enAvRollene.isNotEmpty()) { "postMedRolle må ha minst én rolle" }
    val roller = enAvRollene.joinToString(" eller ") { "`$it`" }
    return post(path, {
        if (dokumentasjon == null) {
            hidden = true
        } else {
            securitySchemeNames(AZURE_AD)
            response {
                code(HttpStatusCode.Unauthorized) {
                    description = "Tokenet mangler eller er ugyldig."
                    problemJson()
                }
                code(HttpStatusCode.Forbidden) {
                    description = "Tokenet har ikke rollen $roller."
                    problemJson()
                }
                code(HttpStatusCode.InternalServerError) {
                    description = "Uventet feil. Prøv igjen senere."
                    problemJson()
                }
            }
            dokumentasjon()
            description = listOfNotNull(description, "Krever et Azure AD-token med rollen $roller.").joinToString("\n\n")
        }
    }) {
        if (!call.harTilgangTil(enAvRollene.toSet())) return@post call.respond(HttpStatusCode.Forbidden)
        body()
    }
}

// Tar bare imot OBO-tokens, altså tokens for en saksbehandler, med scopet appen har fått på inbound-regelen sin.
// Nais fraråder å bruke azp_name til tilgangsstyring, fordi navnet ikke er garantert unikt.
internal fun Route.postMedOboToken(
    path: String,
    scope: String,
    dokumentasjon: RouteConfig.() -> Unit,
    body: suspend RoutingContext.(Saksbehandler) -> Unit,
): Route =
    post(path, {
        securitySchemeNames(AZURE_AD)
        response {
            code(HttpStatusCode.Unauthorized) {
                description = "Tokenet mangler eller er ugyldig."
                problemJson()
            }
            code(HttpStatusCode.Forbidden) {
                description = "Tokenet er ikke et OBO-token for en saksbehandler, eller mangler scopet `$scope`."
                problemJson()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Uventet feil. Prøv igjen senere."
                problemJson()
            }
        }
        dokumentasjon()
        description =
            listOfNotNull(
                description,
                "Krever et OBO-token for en saksbehandler med scopet `$scope`. I dev godtas også OBO-tokens for medlemmer av utviklergruppen.",
            ).joinToString("\n\n")
    }) {
        val saksbehandler = call.oboSaksbehandlerMedScope(scope) ?: return@post call.respond(HttpStatusCode.Forbidden)
        body(saksbehandler)
    }

// Sjekker kjerneregelsettet for hver person før noe hentes. Avslag for én av personene stopper hele kallet.
internal suspend fun RoutingContext.medKjernetilgangTil(
    personidentifikatorer: Set<Personidentifikator>,
    saksbehandler: Saksbehandler,
    tilgangskontroll: PopulasjonstilgangskontrollProvider,
    auditlogg: Auditlogg,
    block: suspend () -> Unit,
) {
    personidentifikatorer.forEach { personidentifikator ->
        val resultat =
            try {
                withContext(Dispatchers.IO) {
                    tilgangskontroll.kontrollerKjerneTilgang(saksbehandler.accessToken, personidentifikator.toString())
                }
            } catch (feil: CancellationException) {
                throw feil
            } catch (feil: Exception) {
                logger.error("Kunne ikke sjekke tilgangen hos Tilgangsmaskinen", feil, "personidentifikator" to personidentifikator.toString())
                return call.respond(HttpStatusCode.ServiceUnavailable)
            }
        when (resultat) {
            TilgangskontrollResultat.Ok -> {}
            is TilgangskontrollResultat.ManglerTilgang -> {
                auditlogg.avslått(saksbehandler, personidentifikator)
                logger.info(
                    "Saksbehandleren mangler tilgang til personen",
                    "personidentifikator" to personidentifikator.toString(),
                    "tilgangSomMangler" to resultat.tilgangSomMangler.name,
                )
                return call.respond(HttpStatusCode.Forbidden)
            }
            TilgangskontrollResultat.IdentIkkeFunnet -> {
                logger.info("Tilgangsmaskinen fant ikke personen", "personidentifikator" to personidentifikator.toString())
                return call.respond(HttpStatusCode.NotFound)
            }
            is TilgangskontrollResultat.UventetFeil -> {
                logger.error(
                    "Uventet svar fra Tilgangsmaskinen",
                    "personidentifikator" to personidentifikator.toString(),
                    "forklaring" to resultat.menneskeligLesbarForklaring,
                )
                return call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }
    }
    block()
}

internal class Saksbehandler(
    val navIdent: String,
    val accessToken: String,
)

private val ApplicationCall.navIdent get() =
    this
        .principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
        .takeUnless { it.isNullOrBlank() }

private val ApplicationCall.scopes get() =
    this
        .principal<JWTPrincipal>()
        ?.getClaim("scp", String::class)
        ?.split(" ")
        ?: emptyList()

private val ApplicationCall.accessToken get() = request.authorization()?.removePrefix("Bearer ")

private fun ApplicationCall.oboSaksbehandlerMedScope(scope: String): Saksbehandler? {
    val endepunkt = request.path()
    val harScope = scope in scopes
    // Utviklere får aldri tilgang utenfor dev, selv om UTVIKLERGRUPPE skulle bli satt der.
    val erUtviklerIDev = application.attributes[MiljøKey] == Miljø.DEV && erUtvikler()
    val navIdent = navIdent
    val accessToken = accessToken
    if (navIdent == null || accessToken == null || !(harScope || erUtviklerIDev)) {
        logger.error(
            "Tokenet er ikke et OBO-token med scopet endepunktet krever",
            "endepunkt" to endepunkt,
            "applicationName" to applicationName,
            "applicationId" to applicationId,
            "påkrevdScope" to scope,
            "erOboToken" to (navIdent != null).toString(),
        )
        return null
    }
    logger.info("Håndterer request med OBO-token", "endepunkt" to endepunkt, "applicationName" to applicationName, "applicationId" to applicationId)
    return Saksbehandler(navIdent, accessToken)
}

private val ApplicationCall.applicationId get() =
    this
        .principal<JWTPrincipal>()
        ?.getClaim("azp", String::class)
        .takeUnless { it.isNullOrBlank() }
        ?: "n/a"

private val ApplicationCall.applicationName get() =
    this
        .principal<JWTPrincipal>()
        ?.getClaim("azp_name", String::class)
        .takeUnless { it.isNullOrBlank() }
        ?: "n/a"

private val ApplicationCall.roles get() =
    this
        .principal<JWTPrincipal>()
        ?.getListClaim("roles", String::class)
        ?: emptyList()

private val ApplicationCall.groups get() =
    this
        .principal<JWTPrincipal>()
        ?.getListClaim("groups", String::class)
        ?: emptyList()

// Settes bare i dev, slik at teamet kan kalle endepunktene med et OBO-token fra azure-token-generator.
internal val UtviklergruppeKey = AttributeKey<String>("utviklergruppe")

internal val MiljøKey = AttributeKey<Miljø>("miljø")

private fun ApplicationCall.erUtvikler() = application.attributes.getOrNull(UtviklergruppeKey)?.let { it in groups } ?: false

private fun ApplicationCall.harTilgangTil(enAvRollene: Set<String>): Boolean {
    val endepunkt = request.path()
    if (erUtvikler()) {
        logger.info("Utvikler håndterer request til $endepunkt")
        return true
    }
    val rolle = enAvRollene.firstOrNull { it in roles }
    if (rolle == null) {
        logger.error(
            "Applikasjon mangler rolle for endepunkt",
            "endepunkt" to endepunkt,
            "applicationName" to applicationName,
            "applicationId" to applicationId,
            "påkrevdeRoller" to enAvRollene.toString(),
            "roller" to roles.toString(),
        )
        return false
    }

    logger.info(
        "Håndterer request til $endepunkt",
        "applicationName" to applicationName,
        "applicationId" to applicationId,
        "rolle" to rolle,
    )
    return true
}
