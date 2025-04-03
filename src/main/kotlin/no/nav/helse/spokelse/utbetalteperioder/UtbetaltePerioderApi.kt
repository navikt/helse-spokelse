package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType.Application.Json
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.Tilgangsstyring

private val objectMapper = jacksonObjectMapper()

internal fun Route.utbetaltePerioderApi(utbetaltePerioder: UtbetaltePerioder) {
    post("/utbetalte-perioder") {
        Tilgangsstyring.utbetaltePerioder(call)
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request)
        call.respondText(response, Json)
    }
    post("/utbetalte-perioder-aap") {
        Tilgangsstyring.utbetaltePerioderAap(call)
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request, groupBy = setOf(GroupBy.grad), tagsFilter = IngenTags)
        call.respondText(response, Json)
    }
    post("/utbetalte-perioder-dagpenger") {
        Tilgangsstyring.utbetaltePerioderDagpenger(call)
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request, groupBy = setOf(GroupBy.grad), tagsFilter = IngenTags)
        call.respondText(response, Json)
    }
}
