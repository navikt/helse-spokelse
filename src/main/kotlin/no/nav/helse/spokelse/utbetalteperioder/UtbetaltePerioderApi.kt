package no.nav.helse.spokelse.utbetalteperioder

import io.ktor.http.ContentType.Application.Json
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.postMedRolle
import tools.jackson.module.kotlin.jacksonObjectMapper

private val objectMapper = jacksonObjectMapper()

internal fun Route.utbetaltePerioderApi(utbetaltePerioder: UtbetaltePerioder) {
    postMedRolle("/utbetalte-perioder", "spleiselaget-les") {
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request)
        call.respondText(response, Json)
    }
    postMedRolle("/utbetalte-perioder-aap", "aap-les") {
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request, groupBy = setOf(GroupBy.grad), tagsFilter = IngenTags)
        call.respondText(response, Json)
    }
    postMedRolle("/utbetalte-perioder-dagpenger", "dagpenger-les") {
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request, groupBy = setOf(GroupBy.grad), tagsFilter = IngenTags)
        call.respondText(response, Json)
    }
    postMedRolle("/utbetalte-perioder-personoversikt", "personoversikt-les") {
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request, groupBy = setOf(GroupBy.grad, GroupBy.organisasjonsnummer, GroupBy.kilde), tagsFilter = AlleTags)
        call.respondText(response, Json)
    }
}
