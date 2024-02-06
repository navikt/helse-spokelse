package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType.Application.Json
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.ApiTilgangsstyring

private val objectMapper = jacksonObjectMapper()
internal fun Route.utbetaltePerioderApi(utbetaltePerioder: UtbetaltePerioder, tilgangsstyrings: ApiTilgangsstyring) {
    post("/utbetalte-perioder") {
        tilgangsstyrings.utbetaltePerioder(call)
        val request = objectMapper.readTree(call.receiveText())
        val response = utbetaltePerioder.hent(request)
        call.respondText(response, Json)
    }
}
