package no.nav.helse.spokelse.gjenstaendesykedager

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import no.nav.helse.spokelse.Tilgangsstyring
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

private val logger = navngittLogger("no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerApi")
private val objectMapper = jacksonObjectMapper()

internal enum class Feilkode {
    INGEN_VEDTAK,
    SISTE_UTBETALING_I_INFOTRYGD,
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

internal fun Route.gjenståendeSykedagerApi(gjenståendeSykedager: GjenståendeSykedager) {
    post("/gjenstaende-sykedager") {
        if (!Tilgangsstyring.harTilgangTilGjenståendeSykedager(call)) return@post call.respond(HttpStatusCode.Forbidden)

        val personidentifikator =
            personidentifikatorFraRequest()
                ?: return@post respondFeil(HttpStatusCode.BadRequest, Feilkode.UGYLDIG_FORESPØRSEL, "Forventer en body med fødselsnummer på 11 siffer")

        when (val resultat = gjenståendeSykedager.hent(personidentifikator)) {
            GjenståendeSykedager.Resultat.IngenVedtak -> {
                logger.info("Fant ikke vedtak for /gjenstaende-sykedager", "fødselsnummer" to personidentifikator.toString())
                respondFeil(HttpStatusCode.NotFound, Feilkode.INGEN_VEDTAK, "Fant ingen vedtak for personen i ny løsning")
            }

            GjenståendeSykedager.Resultat.SisteUtbetalingIInfotrygd -> {
                logger.info("Siste utbetaling er i Infotrygd for /gjenstaende-sykedager", "fødselsnummer" to personidentifikator.toString())
                respondFeil(HttpStatusCode.NotFound, Feilkode.SISTE_UTBETALING_I_INFOTRYGD, "Infotrygd har en utbetaling etter siste vedtak i ny løsning")
            }

            is GjenståendeSykedager.Resultat.Svar -> {
                logger.info(
                    "Svarer på /gjenstaende-sykedager",
                    "fødselsnummer" to personidentifikator.toString(),
                    "gjenståendeSykedager" to resultat.gjenståendeSykedager.toString(),
                    "gjelderTilOgMed" to resultat.gjelderTilOgMed.toString(),
                )
                call.respond(HttpStatusCode.OK, GjenståendeSykedagerResponse(resultat.gjenståendeSykedager, resultat.gjelderTilOgMed))
            }
        }
    }
}

private suspend fun RoutingContext.personidentifikatorFraRequest(): Personidentifikator? {
    val body = runCatching { objectMapper.readTree(call.receiveText()) }.getOrNull() ?: return null
    val fødselsnummer = body.path("fødselsnummer").takeIf { it.isString }?.asString() ?: return null
    return runCatching { Personidentifikator(fødselsnummer) }.getOrNull()
}

private suspend fun RoutingContext.respondFeil(
    status: HttpStatusCode,
    feilkode: Feilkode,
    melding: String,
) = call.respond(status, Feil(feilkode, melding))
