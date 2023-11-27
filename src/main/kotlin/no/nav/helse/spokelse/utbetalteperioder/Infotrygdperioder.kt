package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.rapids_rivers.isMissingOrNull
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.*

internal class Infotrygdperioder(private val infotrygd: Infotrygd, private val personidentifikatorer: Set<Personidentifikator>, private val fom: LocalDate, private val tom: LocalDate) {

    suspend operator fun plus(spleisPerioder: SpleisPerioder): List<SpøkelsePeriode> {
        return spleisPerioder + infotrygd.hent(personidentifikatorer, fom, tom)
    }

}

internal class Infotrygd(private val httpClient: HttpClient, private val scope :String, private val accessToken: AccessToken, private val url: String) {
    suspend fun hent(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate): List<SpøkelsePeriode> {
        val response = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer ${accessToken.get(scope)}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.XCorrelationId, "${UUID.randomUUID()}")
            @Language("JSON")
            val request = """
                {
                    "personidentifikatorer": ${personidentifikatorer.map { "\"$it\"" }},
                    "fom": "$fom",
                    "tom": "$tom"
                }
            """
            setBody(request)
        }

        check(response.status == HttpStatusCode.OK) {
            "Mottok HTTP ${response.status} ved oppslag på Infotrygddata"
        }

        return objectMapper.readTree(response.bodyAsText()).perioder()
    }

    private fun JsonNode.perioder(): List<SpøkelsePeriode> =
        path("utbetaltePerioder").map { it.somPeriode() }
    private fun JsonNode.somPeriode(): SpøkelsePeriode {
        return SpøkelsePeriode(
            personidentifikator = Personidentifikator(path("personidentifikator").asText()),
            fom = LocalDate.parse(path("fom").asText()),
            tom = LocalDate.parse(path("tom").asText()),
            grad = path("grad").asInt(),
            organisasjonsnummer = path("organisasjonsnummer").takeUnless { it.isMissingOrNull() }?.asText(),
            kilde = "Infotrygd"
        )
    }
    private companion object {
        val objectMapper = jacksonObjectMapper()
    }
}
