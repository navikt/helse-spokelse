package no.nav.helse.spokelse.gjenstaendesykedager

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import no.nav.helse.spokelse.Tilgangsstyring
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

private val logger = navngittLogger("no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerApi")
private val objectMapper = jacksonObjectMapper()
private val gyldigFødselsnummer = Regex("\\d{11}")

internal enum class Feilkode {
    INGEN_VEDTAK,
    UGYLDIG_FORESPØRSEL,
}

internal data class Feil(
    val feilkode: Feilkode,
    val melding: String,
)

internal data class GjenståendeSykedagerResponse(
    val gjenståendeSykedager: Int,
    val gjelderTilOgMed: LocalDate,
)

internal fun Route.gjenståendeSykedagerApi(dao: GjenståendeSykedagerDao) {
    post("/gjenstaende-sykedager") {
        if (!Tilgangsstyring.harTilgangTilGjenståendeSykedager(call)) return@post call.respond(HttpStatusCode.Forbidden)

        val fødselsnummer =
            fødselsnummerFraRequest()
                ?: return@post respondFeil(HttpStatusCode.BadRequest, Feilkode.UGYLDIG_FORESPØRSEL, "Forventer en body med fødselsnummer på 11 siffer")

        val sisteVedtak =
            dao.sisteVedtak(fødselsnummer)
                ?: run {
                    logger.info("Fant ikke vedtak for /gjenstaende-sykedager", "fødselsnummer" to fødselsnummer)
                    return@post respondFeil(HttpStatusCode.NotFound, Feilkode.INGEN_VEDTAK, "Fant ingen vedtak for personen i ny løsning")
                }

        logger.info(
            "Svarer på /gjenstaende-sykedager",
            "fødselsnummer" to fødselsnummer,
            "gjenståendeSykedager" to sisteVedtak.gjenståendeSykedager.toString(),
            "gjelderTilOgMed" to sisteVedtak.gjelderTilOgMed.toString(),
        )
        call.respond(HttpStatusCode.OK, GjenståendeSykedagerResponse(sisteVedtak.gjenståendeSykedager, sisteVedtak.gjelderTilOgMed))
    }
}

private suspend fun RoutingContext.fødselsnummerFraRequest(): String? {
    val body = runCatching { objectMapper.readTree(call.receiveText()) }.getOrNull() ?: return null
    val fødselsnummer = body.path("fødselsnummer").takeIf { it.isString }?.asText() ?: return null
    return fødselsnummer.takeIf { gyldigFødselsnummer.matches(it) }
}

private suspend fun RoutingContext.respondFeil(
    status: HttpStatusCode,
    feilkode: Feilkode,
    melding: String,
) = call.respond(status, Feil(feilkode, melding))
