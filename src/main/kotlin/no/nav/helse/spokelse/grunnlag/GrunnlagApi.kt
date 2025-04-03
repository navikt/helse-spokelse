package no.nav.helse.spokelse.grunnlag

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import no.nav.helse.spokelse.Tilgangsstyring

private val logg: Logger = LoggerFactory.getLogger("spokelse")
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val objectMapper = jacksonObjectMapper()
private fun String.asLocalDateOrNull() = kotlin.runCatching { LocalDate.parse(this) }.getOrNull()

internal fun Route.grunnlagApi(gamleUtbetalingerDao: GamleUtbetalingerDao, tbdUtbetalingApi: TbdUtbetalingApi) {
    suspend fun RoutingContext.respond(fødselsnummer: String, fom: LocalDate?) {
        Tilgangsstyring.grunnlag(call)
        val time = measureTimeMillis {
            try {
                val vedtak: List<FpVedtak> = gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, fom).somFpVedtak() + tbdUtbetalingApi.utbetalinger(fødselsnummer, fom, null).somFpVedtak()
                call.respond(HttpStatusCode.OK, vedtak)
            } catch (e: Exception) {
                logg.error("Feil ved henting av vedtak", e)
                sikkerlogg.error("Feil ved henting av vedtak for $fødselsnummer", e)
                call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
            }
        }
        sikkerlogg.info("FP hentet vedtak for $fødselsnummer fra og med $fom ($time ms)")
    }

    post("/grunnlag") {
        val request = objectMapper.readTree(call.receiveText())
        val fødselsnummer = request.path("fodselsnummer").takeUnless { it.isMissingOrNull() }?.asText()
            ?: run {
                logg.error("/grunnlag Mangler fodselsnummer i request body")
                return@post call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer request body")
            }
        val fom = request.path("fom").takeUnless { it.isMissingOrNull() }?.asText()?.let {
            it.asLocalDateOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "Ugyldig fom request body")
        }
        respond(fødselsnummer, fom)
    }
}
