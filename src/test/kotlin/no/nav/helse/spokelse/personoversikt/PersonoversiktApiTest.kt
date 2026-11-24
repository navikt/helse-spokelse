package no.nav.helse.spokelse.personoversikt

import io.ktor.http.ContentType
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.tbdutbetaling.Oppdrag
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje
import no.nav.helse.spokelse.tbdutbetaling.utbetalingUtbetalt
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

internal class PersonoversiktApiTest : AbstractE2ETest() {
    @Test
    fun `gir perioder fra Speil med yrkesaktiviteter, mottakere og utbetalingsperioder`() {
        utbetalingFraSpeil(
            arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-01"), dato("2024-01-31"), 100.0))),
            personOppdrag = Oppdrag("person", listOf(Utbetalingslinje(dato("2024-01-16"), dato("2024-01-31"), 100.0))),
        )

        assertPersonoversikt(
            forventetResponseBody = """
                {
                  "perioder": [
                    {
                      "kilde": "SPEIL",
                      "versjon": "SPEIL_V4",
                      "fom": "2024-01-01",
                      "tom": "2024-01-31",
                      "yrkesaktiviteter": [
                        {
                          "organisasjonsnummer": "$ORGNR",
                          "mottakere": [
                            { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2024-01-01", "tom": "2024-01-31", "grad": 100 } ] },
                            { "mottaker": "PERSON", "utbetalingsperioder": [ { "fom": "2024-01-16", "tom": "2024-01-31", "grad": 100 } ] }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """,
        )
    }

    @Test
    fun `gir én periode per versjon, med mottaker og usikkerGrad der versjonen har dem`() {
        lagreGammelUtbetaling(dato("2020-01-01"), dato("2020-01-31"), "FAG1", FØDSELSNUMMER, fagområde = "SPREF")
        lagreOldVedtakOgOldUtbetaling(dato("2020-03-02"), dato("2020-03-31"), "FAG2", FØDSELSNUMMER)
        lagreVedtakOppdragOgUtbetaling(dato("2020-05-01"), dato("2020-05-29"), "FAG3", FØDSELSNUMMER, fagområde = "SP")
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-01"), dato("2024-01-31"), 100.0))))
        infotrygdperioder =
            listOf(
                Infotrygdperiode(dato("2024-03-01"), dato("2024-03-31"), type = "ARB_REF", organisasjonsnummer = ORGNR, tags = setOf("UsikkerGrad")),
                Infotrygdperiode(dato("2024-03-01"), dato("2024-03-31"), type = "UTBETALING", organisasjonsnummer = null),
            )

        assertPersonoversikt(
            requestBody = """{"personidentifikatorer": ["$FØDSELSNUMMER"], "fom": "2020-01-01", "tom": "2024-12-31"}""",
            forventetResponseBody = """
                {
                  "perioder": [
                    {
                      "kilde": "SPEIL", "versjon": "SPEIL_V1", "fom": "2020-01-01", "tom": "2020-01-31",
                      "yrkesaktiviteter": [ { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2020-01-01", "tom": "2020-01-31", "grad": 100 } ] } ] } ]
                    },
                    {
                      "kilde": "SPEIL", "versjon": "SPEIL_V2", "fom": "2020-03-02", "tom": "2020-03-31",
                      "yrkesaktiviteter": [ { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "utbetalingsperioder": [ { "fom": "2020-03-02", "tom": "2020-03-31", "grad": 100 } ] } ] } ]
                    },
                    {
                      "kilde": "SPEIL", "versjon": "SPEIL_V3", "fom": "2020-05-01", "tom": "2020-05-29",
                      "yrkesaktiviteter": [ { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "PERSON", "utbetalingsperioder": [ { "fom": "2020-05-01", "tom": "2020-05-29", "grad": 100 } ] } ] } ]
                    },
                    {
                      "kilde": "SPEIL", "versjon": "SPEIL_V4", "fom": "2024-01-01", "tom": "2024-01-31",
                      "yrkesaktiviteter": [ { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2024-01-01", "tom": "2024-01-31", "grad": 100 } ] } ] } ]
                    },
                    {
                      "kilde": "INFOTRYGD", "versjon": "INFOTRYGD_V1", "fom": "2024-03-01", "tom": "2024-03-31",
                      "yrkesaktiviteter": [
                        { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2024-03-01", "tom": "2024-03-31", "grad": 100, "usikkerGrad": true } ] } ] },
                        { "organisasjonsnummer": null, "mottakere": [ { "mottaker": "PERSON", "utbetalingsperioder": [ { "fom": "2024-03-01", "tom": "2024-03-31", "grad": 100, "usikkerGrad": false } ] } ] }
                      ]
                    }
                  ]
                }
            """,
        )
    }

    @Test
    fun `ingen utbetalinger gir tom liste`() {
        assertPersonoversikt(forventetResponseBody = """{ "perioder": [] }""")
    }

    @Test
    fun `maskin-til-maskin-token fra Personoversikt gir 403`() {
        assertPersonoversikt(forventetHttpStatus = 403, claims = mapOf("azp_name" to "dev-gcp:personoversikt:modiapersonoversikt-api"))
    }

    @Test
    fun `OBO-token fra en annen app gir 403`() {
        assertPersonoversikt(forventetHttpStatus = 403, claims = mapOf("NAVident" to "A123456", "azp_name" to "dev-gcp:nais:azure-token-generator"))
    }

    @Test
    fun `uten token gir 401`() {
        assertPersonoversikt(forventetHttpStatus = 401, claims = emptyMap())
    }

    @Test
    fun `endepunktet finnes ikke når v2 ikke er åpnet`() {
        personoversiktV2ErÅpen = false
        assertPersonoversikt(forventetHttpStatus = 404)
    }

    @ParameterizedTest
    @MethodSource("ugyldigeForespørsler")
    fun `ugyldig forespørsel gir 400 med problem+json`(requestBody: String) {
        val svar =
            assertApiRequest(
                path = "v2/utbetalte-perioder-personoversikt",
                requestBody = requestBody,
                forventetHttpStatus = 400,
                forventetContentType = ContentType.Application.ProblemJson,
                rolle = null,
                app = null,
                claims = OBO_FRA_MODIA,
            )
        assertEquals("urn:error:bad_request", jacksonObjectMapper().readTree(svar).path("type").asString())
    }

    private fun utbetalingFraSpeil(
        arbeidsgiverOppdrag: Oppdrag? = null,
        personOppdrag: Oppdrag? = null,
    ) = tbdUtbetalingHåndterer.håndter(
        utbetalingUtbetalt(
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = ORGNR,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            personOppdrag = personOppdrag,
        ),
        LocalDateTime.parse("2024-02-01T12:00:00"),
    )

    private fun assertPersonoversikt(
        forventetHttpStatus: Int = 200,
        requestBody: String = """{"personidentifikatorer": ["$FØDSELSNUMMER"], "fom": "2024-01-01", "tom": "2024-12-31"}""",
        claims: Map<String, String> = OBO_FRA_MODIA,
        forventetResponseBody: String? = null,
    ) = assertApiRequest(
        path = "v2/utbetalte-perioder-personoversikt",
        requestBody = requestBody,
        forventetHttpStatus = forventetHttpStatus,
        forventetResponseBody = forventetResponseBody,
        forventetContentType = forventetResponseBody?.let { ContentType.Application.Json },
        rolle = null,
        app = null,
        claims = claims,
    )

    private companion object {
        private val FØDSELSNUMMER = lagIdentitetsnummer()
        private const val ORGNR = "999999999"
        private val OBO_FRA_MODIA = mapOf("NAVident" to "A123456", "azp_name" to "dev-gcp:personoversikt:modiapersonoversikt-api")

        private fun dato(tekst: String) = LocalDate.parse(tekst)

        @JvmStatic
        fun ugyldigeForespørsler() =
            listOf(
                """{"personidentifikatorer": [], "fom": "2024-01-01", "tom": "2024-01-31"}""",
                """{"personidentifikatorer": ["123"], "fom": "2024-01-01", "tom": "2024-01-31"}""",
                """{"personidentifikatorer": ["$FØDSELSNUMMER"], "tom": "2024-01-31"}""",
                """{"personidentifikatorer": ["$FØDSELSNUMMER"], "fom": "2024-01-31", "tom": "2024-01-01"}""",
                """ikke json""",
            )
    }
}
