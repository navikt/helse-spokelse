package no.nav.helse.spokelse.grunnlag

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.postMedRolle
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.treeToValue
import kotlin.system.measureTimeMillis

private val logger = navngittLogger("no.nav.helse.spokelse.grunnlag.GrunnlagApi")

private val objectMapper = jacksonObjectMapper()

internal fun Route.grunnlagApi(
    gamleUtbetalingerDao: GamleUtbetalingerDao,
    tbdUtbetalingApi: TbdUtbetalingApi,
) {
    postMedRolle("/grunnlag", "foreldrepenger-les", "k9-les") {
        val json = objectMapper.readTree(call.receiveText())
        val request = if (json.isObject) objectMapper.treeToValue<ApiGrunnlagRequest>(json) else ApiGrunnlagRequest()
        when (val validering = request.valider()) {
            is UgyldigGrunnlagRequest -> {
                if (validering == UgyldigGrunnlagRequest.MANGLER_FØDSELSNUMMER) {
                    logger.error("/grunnlag Mangler fodselsnummer i request body")
                }
                call.respond(HttpStatusCode.BadRequest, validering.feilmelding)
            }

            is GyldigGrunnlagRequest -> {
                val time =
                    measureTimeMillis {
                        try {
                            val vedtak: List<FpVedtak> =
                                gamleUtbetalingerDao
                                    .hentUtbetalinger(validering.fødselsnummer, validering.fom)
                                    .somFpVedtak() +
                                    tbdUtbetalingApi
                                        .utbetalinger(
                                            validering.fødselsnummer,
                                            validering.fom,
                                            null,
                                        ).somFpVedtak()
                            call.respond<List<ApiGrunnlagVedtak>>(HttpStatusCode.OK, vedtak.tilApiGrunnlagVedtak())
                        } catch (e: Exception) {
                            logger.error("Feil ved henting av vedtak", e, "fødselsnummer" to validering.fødselsnummer)
                            call.respond<String>(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
                        }
                    }
                logger.info(
                    "Hentet vedtak for /grunnlag ($time ms)",
                    "fødselsnummer" to validering.fødselsnummer,
                    "fom" to validering.fom?.toString(),
                )
            }
        }
    }
}
