package no.nav.helse.spokelse.personoversikt

import com.auth0.jwt.JWT
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.http.ContentType
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.AbstractE2ETest.Companion.UTVIKLERGRUPPE
import no.nav.helse.spokelse.Miljø
import no.nav.helse.spokelse.felles.FakeAuditlogg
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.tbdutbetaling.Oppdrag
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje
import no.nav.helse.spokelse.tbdutbetaling.utbetalingUtbetalt
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
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
    fun `audit-logger hver person i oppslaget`() {
        val annenPerson = lagIdentitetsnummer()

        assertPersonoversikt(requestBody = """{"personidentifikatorer": ["$FØDSELSNUMMER", "$annenPerson"], "fom": "2024-01-01", "tom": "2024-12-31"}""")

        assertEquals(
            listOf(
                FakeAuditlogg.Innslag(NAV_IDENT, Personidentifikator(FØDSELSNUMMER), FakeAuditlogg.Utfall.LEST),
                FakeAuditlogg.Innslag(NAV_IDENT, Personidentifikator(annenPerson), FakeAuditlogg.Utfall.LEST),
            ),
            auditlogg.innslag,
        )
    }

    @Test
    fun `avslag for én av personene gir 403 og audit-logges som Deny`() {
        val skjermetPerson = lagIdentitetsnummer()
        tilgangsmaskinen.svar(skjermetPerson, TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt))

        assertPersonoversikt(
            forventetHttpStatus = 403,
            requestBody = """{"personidentifikatorer": ["$FØDSELSNUMMER", "$skjermetPerson"], "fom": "2024-01-01", "tom": "2024-12-31"}""",
        )

        assertEquals(
            listOf(FakeAuditlogg.Innslag(NAV_IDENT, Personidentifikator(skjermetPerson), FakeAuditlogg.Utfall.AVSLÅTT)),
            auditlogg.innslag,
        )
    }

    @Test
    fun `person som Tilgangsmaskinen ikke finner gir 404 uten audit-logging`() {
        tilgangsmaskinen.svar(FØDSELSNUMMER, TilgangskontrollResultat.IdentIkkeFunnet)

        assertPersonoversikt(forventetHttpStatus = 404)

        assertEquals(emptyList<FakeAuditlogg.Innslag>(), auditlogg.innslag)
    }

    @Test
    fun `uventet svar fra Tilgangsmaskinen gir 503 uten audit-logging`() {
        tilgangsmaskinen.svar(FØDSELSNUMMER, TilgangskontrollResultat.UventetFeil("Uventet status fra tilgangsmaskinen: 500"))

        assertPersonoversikt(forventetHttpStatus = 503)

        assertEquals(emptyList<FakeAuditlogg.Innslag>(), auditlogg.innslag)
    }

    @Test
    fun `Tilgangsmaskinen som ikke svarer gir 503 uten audit-logging`() {
        tilgangsmaskinen.feiler(FØDSELSNUMMER, IOException("Connection refused"))

        assertPersonoversikt(forventetHttpStatus = 503)

        assertEquals(emptyList<FakeAuditlogg.Innslag>(), auditlogg.innslag)
    }

    @Test
    fun `Infotrygd nede gir 500 uten audit-logging`() {
        infotrygdperioder = null

        assertPersonoversikt(forventetHttpStatus = 500)

        assertEquals(emptyList<FakeAuditlogg.Innslag>(), auditlogg.innslag)
    }

    @Test
    fun `sjekker tilgangen med OBO-tokenet til saksbehandleren`() {
        assertPersonoversikt()

        val token = JWT.decode(tilgangsmaskinen.accessTokens.single())
        assertEquals(NAV_IDENT, token.getClaim("NAVident").asString())
        assertEquals("defaultaccess personoversikt-les", token.getClaim("scp").asString())
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
    fun `OBO-token fra Personoversikt uten scopet gir 403`() {
        assertPersonoversikt(forventetHttpStatus = 403, claims = OBO_FRA_MODIA - "scp")
    }

    @Test
    fun `OBO-token fra en annen app gir 403`() {
        assertPersonoversikt(forventetHttpStatus = 403, claims = OBO_FRA_TOKENGENERATOR)
    }

    @Test
    fun `OBO-token for en utvikler fra azure-token-generator gir 200 i dev`() {
        assertPersonoversikt(forventetHttpStatus = 200, claims = OBO_FRA_TOKENGENERATOR, grupper = listOf(UTVIKLERGRUPPE))
    }

    @Test
    fun `uten token gir 401`() {
        assertPersonoversikt(forventetHttpStatus = 401, claims = emptyMap())
    }

    @Test
    fun `OBO-token fra Personoversikt gir 200 i prod`() {
        miljø = Miljø.PROD
        assertPersonoversikt(forventetHttpStatus = 200)
    }

    @Test
    fun `OBO-token for en utvikler gir 403 i prod, også når utviklergruppen er satt`() {
        miljø = Miljø.PROD
        utviklergruppe = UTVIKLERGRUPPE
        assertPersonoversikt(forventetHttpStatus = 403, claims = OBO_FRA_TOKENGENERATOR, grupper = listOf(UTVIKLERGRUPPE))
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
        grupper: List<String> = emptyList(),
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
        grupper = grupper,
    )

    private companion object {
        private val FØDSELSNUMMER = lagIdentitetsnummer()
        private const val ORGNR = "999999999"
        private const val NAV_IDENT = "A123456"
        private val OBO_FRA_MODIA =
            mapOf(
                "NAVident" to NAV_IDENT,
                "azp_name" to "dev-gcp:personoversikt:modiapersonoversikt-api",
                "scp" to "defaultaccess personoversikt-les",
            )
        private val OBO_FRA_TOKENGENERATOR = mapOf("NAVident" to "A123456", "azp_name" to "dev-gcp:nais:azure-token-generator")

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
