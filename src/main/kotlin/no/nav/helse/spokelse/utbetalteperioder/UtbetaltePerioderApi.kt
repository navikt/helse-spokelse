package no.nav.helse.spokelse.utbetalteperioder

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import no.nav.helse.spokelse.postMedRolle
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val objectMapper = jacksonObjectMapper()
private val logger = navngittLogger("no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioderApi")

internal fun Route.utbetaltePerioderApi(utbetaltePerioder: UtbetaltePerioder) {
    postMedRolle("/utbetalte-perioder", "spleiselaget-les") {
        svar<ApiUtbetaltePerioderMedOppløsningRequest, _> { request ->
            utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, request.tilGroupBy()).tilApiUtbetaltePerioder(request.tilGroupBy())
        }
    }
    postMedRolle("/utbetalte-perioder-aap", "aap-les") {
        svar<ApiUtbetaltePerioderRequest, _> { request ->
            utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, setOf(GroupBy.grad)).tilApiUtbetaltePerioderMedGrad()
        }
    }
    postMedRolle("/utbetalte-perioder-dagpenger", "dagpenger-les") {
        svar<ApiUtbetaltePerioderRequest, _> { request ->
            utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, setOf(GroupBy.grad)).tilApiUtbetaltePerioderMedGrad()
        }
    }
    postMedRolle("/utbetalte-perioder-personoversikt", "personoversikt-les") {
        svar<ApiUtbetaltePerioderRequest, _> { request ->
            utbetaltePerioder
                .hent(request.tilPersonidentifikatorer(), request.fom, request.tom, setOf(GroupBy.grad, GroupBy.organisasjonsnummer, GroupBy.kilde))
                .tilApiUtbetaltePerioderPersonoversikt()
        }
    }
}

private suspend inline fun <reified Request : Any, reified Response : Any> RoutingContext.svar(lagSvar: (Request) -> Response) {
    val requestTekst = call.receiveText()
    val response = lagSvar(objectMapper.readValue<Request>(requestTekst))
    logger.info("/utbetalte-perioder: request og response", "request" to requestTekst, "response" to objectMapper.writeValueAsString(response))
    call.respond(HttpStatusCode.OK, response)
}
