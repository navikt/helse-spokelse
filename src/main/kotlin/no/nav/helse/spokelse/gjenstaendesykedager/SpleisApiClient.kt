package no.nav.helse.spokelse.gjenstaendesykedager

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.helse.spokelse.utbetalteperioder.AccessToken
import no.nav.helse.spokelse.utbetalteperioder.Infotrygdperiode
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.sykepenger.libs.logging.loggError
import org.slf4j.MDC
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

internal class SpleisApiClient(
    private val httpClient: HttpClient,
    private val scope: String,
    private val accessToken: AccessToken,
    private val url: String,
) : Maksdatoberegning {
    private data class Request(
        val fødselsnummer: String,
        val infotrygdperioder: List<Periode>,
    ) {
        data class Periode(
            val fom: LocalDate,
            val tom: LocalDate,
            val type: String,
            val organisasjonsnummer: String?,
        )
    }

    override suspend fun beregnGjenståendeSykedager(
        personidentifikator: Personidentifikator,
        infotrygdperioder: List<Infotrygdperiode>,
    ): Maksdatoberegning.Beregning {
        val request =
            Request(
                fødselsnummer = personidentifikator.toString(),
                infotrygdperioder = infotrygdperioder.map { Request.Periode(it.fom, it.tom, it.type, it.organisasjonsnummer) },
            )
        val response =
            httpClient.post("$url/api/maksdato") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken.get(scope)}")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header("x-callId", MDC.get("callId"))
                setBody(objectMapper.writeValueAsString(request))
            }

        val responseBody = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            val melding = "Mottok HTTP ${response.status.value} ved beregning av gjenstående sykedager i spleis-api"
            loggError(melding, "responseBody" to responseBody)
            error(melding)
        }

        return objectMapper.readValue<Response>(responseBody).tilBeregning()
    }

    private data class Response(
        val gjenståendeDager: Int,
        val vurdertTilOgMed: LocalDate,
        val maksdato: LocalDate,
    ) {
        fun tilBeregning() = Maksdatoberegning.Beregning(gjenståendeDager, vurdertTilOgMed, maksdato)
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
