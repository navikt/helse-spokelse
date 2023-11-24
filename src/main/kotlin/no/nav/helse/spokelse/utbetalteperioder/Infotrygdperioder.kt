package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.*

class Infotrygdperioder(private val infotrygd: InfotrygdperioderKlient, private val personidentifikatorer: Set<Personidentifikator>, private val fom: LocalDate, private val tom: LocalDate) {

    suspend operator fun plus(speilPerioder: SpeilPerioder): List<SpøkelsePeriode> {
        return infotrygd.data(personidentifikatorer, fom, tom)
    }

}

class InfotrygdperioderKlient(private val httpClient: HttpClient, private val scope :String, private val accessToken: AccessToken, private val url: String) {
    suspend fun data(personidentifikatorer: Set<Personidentifikator>, fom: LocalDate, tom: LocalDate): List<SpøkelsePeriode> =
        jacksonObjectMapper().readTree(httpClient.post(url) {
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
        }.bodyAsText()).perioder()

    private fun JsonNode.perioder(): List<SpøkelsePeriode> =
        path("utbetaltePerioder").map { it.somPeriode() }
    private fun JsonNode.somPeriode(): SpøkelsePeriode {
        return SpøkelsePeriode(
            fom = LocalDate.parse(path("fom").asText()),
            tom = LocalDate.parse(path("tom").asText()),
            grad = path("grad").asInt(),
            kilde = "Infotrygd"
        )
    }
}
