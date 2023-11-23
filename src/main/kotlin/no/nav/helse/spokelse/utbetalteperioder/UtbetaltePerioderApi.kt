package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType.Application.Json
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

private val objectMapper = jacksonObjectMapper()

internal fun Route.utbetaltePerioderApi() {
    post("/utbetalte-perioder") {
        val request = objectMapper.readTree(call.receiveText())
        val personidentifikatorer = request.path("personidentifikatorer")
            .map { Personidentifikator(it.asText()) }
            .toSet()
            .takeUnless { it.isEmpty() } ?: throw IllegalArgumentException("Det må sendes med minst én personidentifikator")
        val fom = LocalDate.parse(request.path("fom").asText())
        val tom = LocalDate.parse(request.path("tom").asText())
        call.respondText("""{"perioder": []}""", Json)
    }
}
