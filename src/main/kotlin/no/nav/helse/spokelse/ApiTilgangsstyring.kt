package no.nav.helse.spokelse

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.util.AttributeKey
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

// Tar bare imot OBO-tokens, altså tokens for en saksbehandler, som appen har vekslet til.
// app er "<namespace>:<app>", slik den står i azp_name ("<cluster>:<namespace>:<app>").
internal fun Route.postMedOboTokenFra(
    path: String,
    app: String,
    dokumentasjon: RouteConfig.() -> Unit,
    body: suspend RoutingContext.() -> Unit,
): Route =
    post(path, {
        securitySchemeNames(AZURE_AD)
        response {
            code(HttpStatusCode.Unauthorized) {
                description = "Tokenet mangler eller er ugyldig."
                problemJson()
            }
            code(HttpStatusCode.Forbidden) {
                description = "Tokenet er ikke et OBO-token for en saksbehandler, eller er ikke utstedt til `$app`."
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
                "Krever et OBO-token for en saksbehandler, utstedt til `$app`. I dev godtas også OBO-tokens for medlemmer av utviklergruppen.",
            ).joinToString("\n\n")
    }) {
        if (!call.erOboTokenFra(app)) return@post call.respond(HttpStatusCode.Forbidden)
        body()
    }

private val ApplicationCall.navIdent get() =
    this
        .principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
        .takeUnless { it.isNullOrBlank() }

private fun ApplicationCall.erOboTokenFra(app: String): Boolean {
    val endepunkt = request.path()
    val erFraApp = applicationName.endsWith(":$app")
    // Utviklere får aldri tilgang utenfor dev, selv om UTVIKLERGRUPPE skulle bli satt der.
    val erUtviklerIDev = application.attributes.getOrNull(ErDevKey) == true && erUtvikler()
    if (navIdent == null || !(erFraApp || erUtviklerIDev)) {
        logger.error(
            "Tokenet er ikke et OBO-token fra appen endepunktet krever",
            "endepunkt" to endepunkt,
            "applicationName" to applicationName,
            "applicationId" to applicationId,
            "påkrevdApp" to app,
            "erOboToken" to (navIdent != null).toString(),
        )
        return false
    }
    logger.info("Håndterer request med OBO-token", "endepunkt" to endepunkt, "applicationName" to applicationName, "applicationId" to applicationId)
    return true
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

internal val ErDevKey = AttributeKey<Boolean>("erDev")

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
