package no.nav.helse.spokelse.felles

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.time.LocalDate

internal class InfotrygdTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `henter alle periodetypene for personen og leser maksdato og organisasjonsnummer`() {
        @Language("JSON")
        val svar =
            """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "$FØDSELSNUMMER", "organisasjonsnummer": "999263550", "fom": "2023-02-01", "tom": "2023-02-28", "grad": 100, "type": "ARB_REF", "maksdato": "2023-12-01" },
                { "personidentifikator": "$FØDSELSNUMMER", "organisasjonsnummer": null, "fom": "2022-06-01", "tom": "2022-06-30", "grad": 0, "type": "FERIE", "maksdato": null }
              ]
            }
            """
        val infotrygd = infotrygd(HttpStatusCode.OK, svar)

        val perioder = runBlocking { infotrygd.hentAllePeriodetyper(Personidentifikator(FØDSELSNUMMER), LocalDate.parse("2000-01-01"), LocalDate.parse("2023-03-01")) }

        assertEquals(
            listOf(
                Infotrygdperiode(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-02-28"), "ARB_REF", LocalDate.parse("2023-12-01"), "999263550"),
                Infotrygdperiode(LocalDate.parse("2022-06-01"), LocalDate.parse("2022-06-30"), "FERIE", null, null),
            ),
            perioder,
        )
        @Language("JSON")
        val forventetRequest =
            """
            {
              "personidentifikatorer": ["$FØDSELSNUMMER"],
              "fom": "2000-01-01",
              "tom": "2023-03-01",
              "inkluderAllePeriodetyper": true
            }
            """
        JSONAssert.assertEquals(forventetRequest, (requests.single().body as TextContent).text, JSONCompareMode.NON_EXTENSIBLE)
    }

    @Test
    fun `svar som ikke er 200 gir unntak`() {
        val infotrygd = infotrygd(HttpStatusCode.InternalServerError, "")
        assertThrows<IllegalStateException> {
            runBlocking { infotrygd.hentAllePeriodetyper(Personidentifikator(FØDSELSNUMMER), LocalDate.parse("2000-01-01"), LocalDate.parse("2023-03-01")) }
        }
    }

    private fun infotrygd(
        status: HttpStatusCode,
        body: String,
    ) = Infotrygd(
        httpClient =
            HttpClient(
                MockEngine { request ->
                    requests.add(request)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        scope = "api://infotrygd",
        accessToken =
            object : AccessToken() {
                override suspend fun hentNytt(scope: String) = "token" to 3600L
            },
        url = "http://infotrygd",
    )

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
    }
}
