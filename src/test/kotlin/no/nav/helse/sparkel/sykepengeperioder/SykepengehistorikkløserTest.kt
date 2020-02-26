package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.AzureClient
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.time.LocalDate

@TestInstance(Lifecycle.PER_CLASS)
internal class SykepengehistorikkløserTest {
    private companion object {
        private val orgnummer = "80000000"
    }

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())

    private lateinit var sendtMelding: JsonNode

    private val context = object : RapidsConnection.MessageContext {
        override fun send(message: String) {
            sendtMelding = objectMapper.readTree(message)
        }

        override fun send(key: String, message: String) {}
    }

    private val rapid = object : RapidsConnection() {

        fun sendTestMessage(message: String) {
            listeners.forEach { it.onMessage(message, context) }
        }

        override fun publish(message: String) {}

        override fun publish(key: String, message: String) {}

        override fun start() {}

        override fun stop() {}
    }

    @BeforeAll
    fun setup() {
        wireMockServer.start()
        configureFor(create().port(wireMockServer.port()).build())
        stubEksterneEndepunkt()
    }

    @AfterAll
    internal fun teardown() {
        wireMockServer.stop()
    }

    @Test
    internal fun `løser behov`() {
        val behov = """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "utgangspunktForBeregningAvYtelse": "2020-01-01", "fødselsnummer": "fnr", "vedtaksperiodeId": "id" }"""

        testBehov(behov)

        val perioder = sendtMelding.løsning()

        assertEquals(1, perioder.size)
        assertEquals(3.januar, perioder[0].fom)
        assertEquals(23.januar, perioder[0].tom)
        assertEquals("100", perioder[0].grad)
    }

    @Test
    internal fun `mapper også ut inntekt og dagsats`() {
        val behov = """{"@id": "behovsid", "@behov":["${Sykepengehistorikkløser.behov}"], "utgangspunktForBeregningAvYtelse": "2020-01-01", "fødselsnummer": "fnr", "vedtaksperiodeId": "id" }"""

        testBehov(behov)

        val perioder = sendtMelding.løsning()

        assertEquals(1, perioder.size)
        assertEquals(3.januar, perioder[0].fom)
        assertEquals(23.januar, perioder[0].tom)

        assertSykeperiode(
                sykeperiode = perioder[0].utbetalteSykeperioder[0],
                fom = 19.januar,
                tom = 23.januar,
                grad = "100",
                orgnummer = orgnummer,
                inntektPerMåned = 18852,
                dagsats = 870.0
        )

        assertInntektsopplysninger(
                inntektsopplysninger = perioder[0].inntektsopplysninger,
                dato = 19.januar,
                inntektPerMåned = 18852,
                orgnummer = orgnummer
        )
    }

    private fun JsonNode.løsning() = this.path("@løsning").path(Sykepengehistorikkløser.behov).map {
        Utbetalingshistorikk(it)
    }

    private class Utbetalingshistorikk(json: JsonNode) {

        val fom = json["fom"].asLocalDate()
        val tom = json["tom"].asLocalDate()
        val grad = json["grad"].asText()
        val utbetalteSykeperioder = json["utbetalteSykeperioder"].map {
            UtbetalteSykeperiode(it)
        }
        val inntektsopplysninger = json["inntektsopplysninger"].map {
            Inntektsopplysning(it)
        }

        class UtbetalteSykeperiode(json: JsonNode) {
            val fom = json["fom"].asLocalDate()
            val tom = json["tom"].asLocalDate()
            val utbetalingsGrad = json["utbetalingsGrad"].asText()
            val orgnummer = json["orgnummer"].asText()
            val inntektPerMåned = json["inntektPerMåned"].asInt()
            val dagsats = json["dagsats"].asDouble()
        }

        class Inntektsopplysning(json: JsonNode) {
            val sykepengerFom = json["sykepengerFom"].asLocalDate()
            val inntekt = json["inntekt"].asInt()
            val orgnummer = json["orgnummer"].asText()
        }

        private companion object {
            fun JsonNode.asLocalDate() = LocalDate.parse(this.asText())
        }
    }

    private fun testBehov(behov: String) {
        val løser = Sykepengehistorikkløser(rapid, InfotrygdClient(
                baseUrl = wireMockServer.baseUrl(),
                accesstokenScope = "a_scope",
                azureClient = AzureClient(
                        tenantUrl = "${wireMockServer.baseUrl()}/AZURE_TENANT_ID",
                        clientId = "client_id",
                        clientSecret = "client_secret"
                )
        ))
        rapid.sendTestMessage(behov)
    }

    private fun assertSykeperiode(
            sykeperiode: Utbetalingshistorikk.UtbetalteSykeperiode,
            fom: LocalDate,
            tom: LocalDate,
            grad: String,
            orgnummer: String,
            inntektPerMåned: Int,
            dagsats: Double
    ) {
        assertEquals(fom, sykeperiode.fom)
        assertEquals(tom, sykeperiode.tom)
        assertEquals(grad, sykeperiode.utbetalingsGrad)
        assertEquals(orgnummer, sykeperiode.orgnummer)
        assertEquals(inntektPerMåned, sykeperiode.inntektPerMåned)
        assertEquals(dagsats, sykeperiode.dagsats)
    }

    private fun assertInntektsopplysninger(
            inntektsopplysninger: List<Utbetalingshistorikk.Inntektsopplysning>,
            dato: LocalDate,
            inntektPerMåned: Int,
            orgnummer: String
    ) {
        assertEquals(dato, inntektsopplysninger[0].sykepengerFom)
        assertEquals(inntektPerMåned, inntektsopplysninger[0].inntekt)
        assertEquals(orgnummer, inntektsopplysninger[0].orgnummer)
    }

    private fun stubEksterneEndepunkt() {
        stubFor(post(urlMatching("/AZURE_TENANT_ID/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }""")))
        stubFor(get(urlPathEqualTo("/v1/hentSykepengerListe"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{
                                      "sykmeldingsperioder": [
                                        {
                                          "ident": 1000,
                                          "tknr": "0220",
                                          "seq": 79999596,
                                          "sykemeldtFom": "2018-01-03",
                                          "sykemeldtTom": "2018-01-23",
                                          "grad": "100",
                                          "slutt": "2019-03-30",
                                          "erArbeidsgiverPeriode": true,
                                          "stansAarsakKode": "AF",
                                          "stansAarsak": "AvsluttetFrisk",
                                          "unntakAktivitet": "",
                                          "arbeidsKategoriKode": "01",
                                          "arbeidsKategori": "Arbeidstaker",
                                          "arbeidsKategori99": "",
                                          "erSanksjonBekreftet": "",
                                          "sanksjonsDager": 0,
                                          "sykemelder": "NØDNUMMER",
                                          "behandlet": "2018-01-05",
                                          "yrkesskadeArt": "",
                                          "utbetalingList": [
                                            {
                                              "fom": "2018-01-19",
                                              "tom": "2018-01-23",
                                              "utbetalingsGrad": "100",
                                              "oppgjorsType": "50",
                                              "utbetalt": "2018-02-16",
                                              "dagsats": 870.0,
                                              "typeKode": "5",
                                              "typeTekst": "ArbRef",
                                              "arbOrgnr": 80000000
                                            }
                                          ],
                                          "inntektList": [
                                            {
                                              "orgNr": "80000000",
                                              "sykepengerFom": "2018-01-19",
                                              "refusjonTom": "2018-01-30",
                                              "refusjonsType": "H",
                                              "periodeKode": "U",
                                              "periode": "Ukentlig",
                                              "loenn": 4350.5
                                            }
                                          ],
                                          "graderingList": [
                                            {
                                              "gradertFom": "2018-01-03",
                                              "gradertTom": "2018-01-23",
                                              "grad": "100"
                                            }
                                          ],
                                          "forsikring": []
                                        }
                                      ]
                                    }""")))
    }
}
