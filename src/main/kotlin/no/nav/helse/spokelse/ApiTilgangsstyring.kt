package no.nav.helse.spokelse

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import no.nav.sykepenger.libs.logging.navngittLogger

private val logger = navngittLogger("no.nav.helse.spokelse.Tilgangsstyring")

internal fun Route.postMedRolle(
    path: String,
    vararg enAvRollene: String,
    body: suspend RoutingContext.() -> Unit,
): Route {
    require(enAvRollene.isNotEmpty()) { "postMedRolle må ha minst én rolle" }
    return post(path) {
        if (!call.harTilgangTil(enAvRollene.toSet())) return@post call.respond(HttpStatusCode.Forbidden)
        body()
    }
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

private fun ApplicationCall.harTilgangTil(enAvRollene: Set<String>): Boolean {
    val endepunkt = request.path()
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
