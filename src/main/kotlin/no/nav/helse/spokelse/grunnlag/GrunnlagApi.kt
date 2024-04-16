package no.nav.helse.spokelse.grunnlag

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.ApiTilgangsstyring
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val logg: Logger = LoggerFactory.getLogger("spokelse")
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private fun String.asLocalDateOrNull() = kotlin.runCatching { LocalDate.parse(this) }.getOrNull()

internal fun Route.grunnlagApi(vedtakDAO: GamleUtbetalingerDao, tbdUtbetalingApi: TbdUtbetalingApi, tilgangsstyrings: ApiTilgangsstyring) {
    get("/grunnlag") {
        tilgangsstyrings.grunnlag(call)
        val fødselsnummer = call.request.queryParameters["fodselsnummer"]
            ?: run {
                logg.error("/grunnlag Mangler fodselsnummer query param")
                return@get call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer query param")
            }
        val fom = call.request.queryParameters["fom"]?.let {
            it.asLocalDateOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Ugyldig fom query param")
        }
        val time = measureTimeMillis {
            try {
                val vedtak = vedtakDAO.hentFpVedtak(fødselsnummer, fom) + tbdUtbetalingApi.hentFpVedtak(fødselsnummer, fom)
                call.respond(HttpStatusCode.OK, vedtak)
            } catch (e: Exception) {
                logg.error("Feil ved henting av vedtak", e)
                sikkerlogg.error("Feil ved henting av vedtak for $fødselsnummer", e)
                call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
            }
        }
        sikkerlogg.info("FP hentet vedtak for $fødselsnummer fra og med $fom ($time ms)")
    }
}
