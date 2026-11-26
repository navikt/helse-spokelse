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
import no.nav.helse.spokelse.tbdvedtak.vedtakAnnullert
import no.nav.helse.spokelse.tbdvedtak.vedtakFattet
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
    fun `en linje med vedtak gir SPEIL_V5 med skjæringstidspunktet fra vedtaket`() {
        vedtakFraSpeil(fom = dato("2024-01-01"), tom = dato("2024-01-31"), skjæringstidspunkt = dato("2023-12-18"))
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-01"), dato("2024-01-31"), 100.0))))

        assertPersonoversikt(
            forventetResponseBody = """
                {
                  "perioder": [
                    {
                      "kilde": "SPEIL", "versjon": "SPEIL_V5", "skjæringstidspunkt": "2023-12-18", "fom": "2024-01-01", "tom": "2024-01-31",
                      "yrkesaktiviteter": [ { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2024-01-01", "tom": "2024-01-31", "grad": 100 } ] } ] } ]
                    }
                  ]
                }
            """,
        )
    }

    @Test
    fun `en linje får skjæringstidspunktet fra det nyeste vedtaket som dekker linjens fom`() {
        vedtakFraSpeil(dato("2024-01-01"), dato("2024-01-31"), skjæringstidspunkt = dato("2024-01-01"), vedtakFattetTidspunkt = LocalDateTime.parse("2024-02-01T11:00:00.123"))
        vedtakFraSpeil(dato("2024-01-01"), dato("2024-01-31"), skjæringstidspunkt = dato("2023-12-18"), vedtakFattetTidspunkt = LocalDateTime.parse("2024-03-01T11:00:00.123"))
        vedtakFraSpeil(dato("2024-01-01"), dato("2024-01-31"), skjæringstidspunkt = dato("2023-12-11"), vedtakFattetTidspunkt = LocalDateTime.parse("2024-02-15T11:00:00.123"))
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-17"), dato("2024-01-31"), 100.0))))

        assertEquals(listOf("SPEIL_V5" to "2023-12-18"), hentPerioder().map { it.path("versjon").asString() to it.path("skjæringstidspunkt").asString() })
    }

    @Test
    fun `en linje uten vedtak, eller med vedtak bare hos en annen arbeidsgiver, forblir SPEIL_V4`() {
        vedtakFraSpeil(dato("2024-01-01"), dato("2024-01-31"), skjæringstidspunkt = dato("2024-01-01"), organisasjonsnummer = "888888888")
        vedtakFraSpeil(dato("2024-02-01"), dato("2024-02-29"), skjæringstidspunkt = dato("2024-01-01"))
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-17"), dato("2024-01-31"), 100.0))))

        assertEquals(listOf("SPEIL_V4"), hentPerioder().map { it.path("versjon").asString() })
    }

    @Test
    fun `et tilfelle som går over D, deles i en periode med SPEIL_V4 og en med SPEIL_V5`() {
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("januar", listOf(Utbetalingslinje(dato("2024-01-17"), dato("2024-01-31"), 100.0))))
        vedtakFraSpeil(dato("2024-02-01"), dato("2024-02-29"), skjæringstidspunkt = dato("2024-01-01"))
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("februar", listOf(Utbetalingslinje(dato("2024-02-01"), dato("2024-02-29"), 100.0))))

        assertEquals(
            listOf(Triple("SPEIL_V4", "2024-01-17", "2024-01-31"), Triple("SPEIL_V5", "2024-02-01", "2024-02-29")),
            hentPerioder().map { Triple(it.path("versjon").asString(), it.path("fom").asString(), it.path("tom").asString()) },
        )
    }

    @Test
    fun `utbetalinger til selvstendig næringsdrivende kobles til vedtaket, uten organisasjonsnummer`() {
        vedtakFraSpeil(dato("2024-01-01"), dato("2024-01-31"), skjæringstidspunkt = dato("2024-01-01"), organisasjonsnummer = "SELVSTENDIG")
        utbetalingFraSpeil(personOppdrag = Oppdrag("person", listOf(Utbetalingslinje(dato("2024-01-17"), dato("2024-01-31"), 100.0))), organisasjonsnummer = "SELVSTENDIG")

        val periode = hentPerioder().single()
        assertEquals("SPEIL_V5", periode.path("versjon").asString())
        assertEquals(
            true,
            periode
                .path("yrkesaktiviteter")
                .single()
                .path("organisasjonsnummer")
                .isNull,
        )
    }

    @Test
    fun `annullerte vedtak på tbd_vedtak ignoreres`() {
        tbdVedtakHåndterer.håndter(vedtakAnnullert(FØDSELSNUMMER, ORGNR, dato("2024-01-01"), dato("2024-01-31")))
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-17"), dato("2024-01-31"), 100.0))))

        assertEquals(listOf("SPEIL_V4"), hentPerioder().map { it.path("versjon").asString() })
    }

    @Test
    fun `gir én periode per versjon, med mottaker, skjæringstidspunkt og usikkerGrad der versjonen har dem`() {
        lagreGammelUtbetaling(dato("2020-01-01"), dato("2020-01-31"), "FAG1", FØDSELSNUMMER, fagområde = "SPREF")
        lagreOldVedtakOgOldUtbetaling(dato("2020-03-02"), dato("2020-03-31"), "FAG2", FØDSELSNUMMER)
        lagreVedtakOppdragOgUtbetaling(dato("2020-05-01"), dato("2020-05-29"), "FAG3", FØDSELSNUMMER, fagområde = "SP")
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(dato("2024-01-01"), dato("2024-01-31"), 100.0))))
        vedtakFraSpeil(dato("2024-05-01"), dato("2024-05-31"), skjæringstidspunkt = dato("2024-04-15"))
        utbetalingFraSpeil(arbeidsgiverOppdrag = Oppdrag("mai", listOf(Utbetalingslinje(dato("2024-05-01"), dato("2024-05-31"), 100.0))))
        infotrygdperioder =
            listOf(
                Infotrygdperiode(dato("2024-03-01"), dato("2024-03-31"), type = "ARB_REF", organisasjonsnummer = ORGNR, tags = setOf("UsikkerGrad"), skjæringstidspunkt = dato("2024-02-12")),
                Infotrygdperiode(dato("2024-03-01"), dato("2024-03-31"), type = "UTBETALING", organisasjonsnummer = null, skjæringstidspunkt = dato("2024-02-12")),
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
                      "kilde": "INFOTRYGD", "versjon": "INFOTRYGD_V1", "skjæringstidspunkt": "2024-02-12", "fom": "2024-03-01", "tom": "2024-03-31",
                      "yrkesaktiviteter": [
                        { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2024-03-01", "tom": "2024-03-31", "grad": 100, "usikkerGrad": true } ] } ] },
                        { "organisasjonsnummer": null, "mottakere": [ { "mottaker": "PERSON", "utbetalingsperioder": [ { "fom": "2024-03-01", "tom": "2024-03-31", "grad": 100, "usikkerGrad": false } ] } ] }
                      ]
                    },
                    {
                      "kilde": "SPEIL", "versjon": "SPEIL_V5", "skjæringstidspunkt": "2024-04-15", "fom": "2024-05-01", "tom": "2024-05-31",
                      "yrkesaktiviteter": [ { "organisasjonsnummer": "$ORGNR", "mottakere": [ { "mottaker": "ARBEIDSGIVER", "utbetalingsperioder": [ { "fom": "2024-05-01", "tom": "2024-05-31", "grad": 100 } ] } ] } ]
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
    fun `en periode fra Infotrygd uten skjæringstidspunkt gir 500 uten audit-logging`() {
        infotrygdperioder = listOf(Infotrygdperiode(dato("2024-03-01"), dato("2024-03-31"), skjæringstidspunkt = null))

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
        organisasjonsnummer: String = ORGNR,
    ) = tbdUtbetalingHåndterer.håndter(
        utbetalingUtbetalt(
            fødselsnummer = FØDSELSNUMMER,
            organisasjonsnummer = organisasjonsnummer,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            personOppdrag = personOppdrag,
        ),
        LocalDateTime.parse("2024-02-01T12:00:00"),
    )

    private fun vedtakFraSpeil(
        fom: LocalDate,
        tom: LocalDate,
        skjæringstidspunkt: LocalDate,
        vedtakFattetTidspunkt: LocalDateTime = LocalDateTime.parse("2024-02-01T11:00:00.123"),
        organisasjonsnummer: String = ORGNR,
    ) = tbdVedtakHåndterer.håndter(vedtakFattet(FØDSELSNUMMER, organisasjonsnummer, fom, tom, skjæringstidspunkt, vedtakFattetTidspunkt))

    private fun hentPerioder() =
        jacksonObjectMapper()
            .readTree(assertPersonoversikt())
            .path("perioder")
            .values()
            .toList()

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
