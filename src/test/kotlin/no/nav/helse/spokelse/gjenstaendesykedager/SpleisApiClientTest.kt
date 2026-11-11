package no.nav.helse.spokelse.gjenstaendesykedager

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import no.nav.helse.spokelse.felles.AccessToken
import no.nav.helse.spokelse.felles.Infotrygdperiode
import no.nav.helse.spokelse.felles.Personidentifikator
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.time.LocalDate

internal class SpleisApiClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `sender fødselsnummeret og periodene i Infotrygd til spleis-api og leser beregningen`() {
        val client = spleisApiClient(HttpStatusCode.OK, """{"gjenståendeDager": 120, "vurdertTilOgMed": "2023-02-28", "maksdato": "2023-08-15"}""")
        val perioder =
            listOf(
                Infotrygdperiode(LocalDate.parse("2022-06-01"), LocalDate.parse("2022-06-30"), "FERIE", null, null),
                Infotrygdperiode(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-02-28"), "ARB_REF", LocalDate.parse("2023-12-01"), "999263550"),
            )

        val beregning = runBlocking { client.beregnGjenståendeSykedager(Personidentifikator(FØDSELSNUMMER), perioder) }

        assertEquals(Maksdatoberegning.Beregning(120, LocalDate.parse("2023-02-28"), LocalDate.parse("2023-08-15")), beregning)
        assertEquals("http://spleis-api/api/maksdato", requests.single().url.toString())
        @Language("JSON")
        val forventetRequest =
            """
            {
              "fødselsnummer": "$FØDSELSNUMMER",
              "infotrygdperioder": [
                { "fom": "2022-06-01", "tom": "2022-06-30", "type": "FERIE", "organisasjonsnummer": null },
                { "fom": "2023-02-01", "tom": "2023-02-28", "type": "ARB_REF", "organisasjonsnummer": "999263550" }
              ]
            }
            """
        JSONAssert.assertEquals(forventetRequest, (requests.single().body as TextContent).text, JSONCompareMode.NON_EXTENSIBLE)
    }

    @Test
    fun `svar som ikke er 200 gir unntak`() {
        listOf(HttpStatusCode.InternalServerError, HttpStatusCode.NotFound).forEach { status ->
            val client = spleisApiClient(status, "")
            assertThrows<IllegalStateException> {
                runBlocking { client.beregnGjenståendeSykedager(Personidentifikator(FØDSELSNUMMER), emptyList()) }
            }
        }
    }

    private fun spleisApiClient(
        status: HttpStatusCode,
        body: String,
    ) = SpleisApiClient(
        httpClient =
            HttpClient(
                MockEngine { request ->
                    requests.add(request)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        scope = "api://spleis-api",
        accessToken =
            object : AccessToken() {
                override suspend fun hentNytt(scope: String) = "token" to 3600L
            },
        url = "http://spleis-api",
    )
}
