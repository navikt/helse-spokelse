package no.nav.helse.spokelse.grunnlag

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.Tilgangsstyring
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val logger = navngittLogger("no.nav.helse.spokelse.grunnlag.GrunnlagApi")
private val objectMapper = jacksonObjectMapper()

private fun String.asLocalDateOrNull() = kotlin.runCatching { LocalDate.parse(this) }.getOrNull()

internal fun Route.grunnlagApi(
    gamleUtbetalingerDao: GamleUtbetalingerDao,
    tbdUtbetalingApi: TbdUtbetalingApi,
) {
    suspend fun RoutingContext.respond(
        fødselsnummer: String,
        fom: LocalDate?,
    ) {
        Tilgangsstyring.grunnlag(call)
        val time =
            measureTimeMillis {
                try {
                    val vedtak: List<FpVedtak> = gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, fom).somFpVedtak() + tbdUtbetalingApi.utbetalinger(fødselsnummer, fom, null).somFpVedtak()
                    call.respond(HttpStatusCode.OK, vedtak)
                } catch (e: Exception) {
                    logger.error("Feil ved henting av vedtak", e, "fødselsnummer" to fødselsnummer)
                    call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
                }
            }
        logger.info("Hentet vedtak for /grunnlag ($time ms)", "fødselsnummer" to fødselsnummer, "fom" to fom?.toString())
    }

    post("/grunnlag") {
        val request = objectMapper.readTree(call.receiveText())
        val fødselsnummer =
            request.path("fodselsnummer").takeUnless { it.isMissingOrNull() }?.asText()
                ?: run {
                    logger.error("/grunnlag Mangler fodselsnummer i request body")
                    return@post call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer request body")
                }
        val fom =
            request.path("fom").takeUnless { it.isMissingOrNull() }?.asText()?.let {
                it.asLocalDateOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "Ugyldig fom request body")
            }
        respond(fødselsnummer, fom)
    }
}
