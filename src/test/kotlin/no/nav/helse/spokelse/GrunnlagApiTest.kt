package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.rapids_rivers.InMemoryRapid
import no.nav.helse.rapids_rivers.inMemoryRapid
import no.nav.helse.spokelse.Environment.Auth.Companion.auth
import org.awaitility.Awaitility.await
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrunnlagApiTest {
    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var hikariConfig: HikariConfig
    private lateinit var dataSource: HikariDataSource

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private lateinit var jwtStub: JwtStub

    private lateinit var dokumentDao: DokumentDao
    private lateinit var vedtakDao: VedtakDao
    private lateinit var utbetaltDao: UtbetaltDao
    private lateinit var rapid: InMemoryRapid

    private lateinit var appBaseUrl: String

    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @BeforeAll
    fun setup() {
        embeddedPostgres = EmbeddedPostgres.builder().start()

        hikariConfig = HikariConfig().apply {
            this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }

        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        dokumentDao = DokumentDao(dataSource)
        vedtakDao = VedtakDao(dataSource)
        utbetaltDao = UtbetaltDao(dataSource)

        //Stub ID provider (for authentication of REST endpoints)
        wireMockServer.start()
        await("vent på WireMockServer har startet")
            .atMost(5, TimeUnit.SECONDS)
            .until {
                try {
                    Socket("localhost", wireMockServer.port()).use { it.isConnected }
                } catch (err: Exception) {
                    false
                }
            }
        jwtStub = JwtStub("Microsoft Azure AD", wireMockServer)
        WireMock.stubFor(jwtStub.stubbedJwkProvider())
        WireMock.stubFor(jwtStub.stubbedConfigProvider())

        val randomPort = ServerSocket(0).use { it.localPort }
        appBaseUrl = "http://localhost:$randomPort"

        rapid = inMemoryRapid {
            ktor {
                port(randomPort)
                module {
                    spokelse(auth(
                        name = "issuer",
                        clientId = "spokelse_azure_ad_app_id",
                        validConsumers = listOf("fp_object_id"),
                        discoveryUrl = "${wireMockServer.baseUrl()}/config"
                    ), dokumentDao, vedtakDao)
                }
            }
        }.apply { start() }


        NyttDokumentRiver(rapid, dokumentDao)
        TilUtbetalingBehovRiver(rapid, dokumentDao)
        OldUtbetalingRiver(rapid, vedtakDao, dokumentDao)
        UtbetaltRiver(rapid, utbetaltDao, dokumentDao)
    }

    @Test
    fun `skriver gammelt vedtak til db`() {
        val fnr = "01010145678"
        val orgnummer = "123456789"

        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        rapid.sendToListeners(sendtSøknadMessage(sykmelding, søknad))
        rapid.sendToListeners(inntektsmeldingMessage(inntektsmelding))
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"
        val fom = LocalDate.of(2020, 4, 1)
        val tom = LocalDate.of(2020, 4, 6)
        val grad = 50.0
        val vedtattTidspunkt = LocalDateTime.of(2020, 4, 11, 10, 0)
        rapid.sendToListeners(
            utbetalingBehov(
                fnr,
                orgnummer,
                vedtaksperiodeId,
                fagsystemId,
                fom,
                tom,
                grad
            )
        )
        rapid.sendToListeners(
            vedtakMedUtbetalingslinjernøkkel(
                fnr,
                orgnummer,
                fom,
                tom,
                grad,
                vedtaksperiodeId,
                vedtattTidspunkt,
                listOf(sykmelding, søknad, inntektsmelding)
            )
        )

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            "/grunnlag?fodselsnummer=$fnr".httpGet {
                objectMapper.readValue<List<FpVedtak>>(this).apply {
                    assertEquals(1, size)

                    assertEquals(fagsystemId, this[0].vedtaksreferanse)
                    assertEquals(vedtattTidspunkt, this[0].vedtattTidspunkt)
                    assertEquals(fom, this[0].utbetalinger[0].fom)
                    assertEquals(tom, this[0].utbetalinger[0].tom)
                    assertEquals(grad, this[0].utbetalinger[0].grad)
                }
            }
        }
    }

    @Test
    fun `skriver vedtak til db`() {
        val fnr = "01010145679"
        val orgnummer = "123456789"

        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        rapid.sendToListeners(sendtSøknadMessage(sykmelding, søknad))
        rapid.sendToListeners(inntektsmeldingMessage(inntektsmelding))
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ5"
        val fom = LocalDate.of(2020, 4, 1)
        val tom = LocalDate.of(2020, 4, 6)
        val grad = 70.0
        val vedtattTidspunkt = LocalDateTime.of(2020, 4, 11, 10, 0)
        rapid.sendToListeners(
            utbetalingMessage(
                fnr,
                orgnummer,
                fagsystemId,
                fom,
                tom,
                grad,
                vedtattTidspunkt,
                listOf(sykmelding, søknad, inntektsmelding)
            )
        )

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            "/grunnlag?fodselsnummer=$fnr".httpGet {
                objectMapper.readValue<List<FpVedtak>>(this).apply {
                    assertEquals(1, size)

                    assertEquals(fagsystemId, this[0].vedtaksreferanse)
                    assertEquals(vedtattTidspunkt, this[0].vedtattTidspunkt)
                    assertEquals(fom, this[0].utbetalinger[0].fom)
                    assertEquals(tom, this[0].utbetalinger[0].tom)
                    assertEquals(grad, this[0].utbetalinger[0].grad)
                }
            }
        }
    }

    @Test
    fun `kall til grunnlag uten fnr gir 400`() {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            "/grunnlag".httpGet(HttpStatusCode.BadRequest)
        }
    }

    @Language("JSON")
    private fun utbetalingMessage(
        fnr: String,
        orgnummer: String,
        fagsystemId: String,
        fom: LocalDate,
        tom: LocalDate,
        grad: Double,
        vedtattTidspunkt: LocalDateTime,
        hendelser: List<Hendelse>
    ) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "$fnr",
    "organisasjonsnummer": "$orgnummer",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalt": [
        {
            "mottaker": "$orgnummer",
            "fagområde": "SPREF",
            "fagsystemId": "$fagsystemId",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "$fom",
                    "tom": "$tom",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": $grad,
                    "sykedager": ${sykedager(fom, tom)}
                }
            ]
        },
        {
            "mottaker": "$fnr",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "$fom",
    "tom": "$tom",
    "forbrukteSykedager": ${sykedager(fom, tom)},
    "gjenståendeSykedager": ${248 - sykedager(fom, tom)},
    "opprettet": "$vedtattTidspunkt",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "cf28fbba-562e-4841-b366-be1456fdccef",
    "@opprettet": "$vedtattTidspunkt",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""


    @Language("JSON")
    private fun vedtakMedUtbetalingslinjernøkkel(
        fnr: String,
        orgnummer: String,
        fom: LocalDate,
        tom: LocalDate,
        grad: Double,
        vedtaksperiodeId: UUID,
        vedtattTidspunkt: LocalDateTime,
        hendelser: List<Hendelse>
    ) = """{
    "førsteFraværsdag": "$fom",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalingslinjer": [
        {
            "fom": "$fom",
            "tom": "$tom",
            "dagsats": 1431,
            "beløp": 1431,
            "grad": $grad,
            "enDelAvPerioden": true,
            "mottaker": "987654321",
            "konto": "SPREF"
        }
    ],
    "forbrukteSykedager": ${sykedager(fom, tom)},
    "gjenståendeSykedager": null,
    "opprettet": "2020-06-10T10:46:36.979478",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "3bcefb15-8fb0-4b9b-99d7-547c0c295820",
    "@opprettet": "$vedtattTidspunkt",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "75e4718f-ae59-4701-a09c-001630bcbd1a",
        "opprettet": "2020-06-10T10:46:37.275083"
    },
    "aktørId": "42",
    "fødselsnummer": "$fnr",
    "organisasjonsnummer": "$orgnummer"
}"""

    @Language("JSON")
    private fun utbetalingBehov(fnr: String, orgnummer: String, vedtaksperiodeId: UUID, fagsystemId: String, fom: LocalDate, tom: LocalDate, grad: Double) = """{
    "@event_name": "behov",
    "@opprettet": "2020-06-10T10:02:21.069247",
    "@id": "65d0df95-2b8f-4ac7-8d73-e0b41f575330",
    "@behov": [
        "Utbetaling"
    ],
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "7eb871b1-8a40-49a6-8f9d-c9da3e3c6d73",
        "opprettet": "2020-06-10T09:59:45.873566"
    },
    "aktørId": "42",
    "fødselsnummer": "$fnr",
    "organisasjonsnummer": "$orgnummer",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "tilstand": "TIL_UTBETALING",
    "mottaker": "$orgnummer",
    "fagområde": "SPREF",
    "linjer": [
        {
            "fom": "$fom",
            "tom": "$tom",
            "dagsats": 1431,
            "lønn": 1431,
            "grad": $grad,
            "refFagsystemId": null,
            "delytelseId": 1,
            "datoStatusFom": null,
            "statuskode": null,
            "refDelytelseId": null,
            "endringskode": "NY",
            "klassekode": "SPREFAG-IOP"
        }
    ],
    "fagsystemId": "$fagsystemId",
    "endringskode": "NY",
    "sisteArbeidsgiverdag": null,
    "nettoBeløp": 8586,
    "saksbehandler": "en_saksbehandler",
    "maksdato": "2019-01-01",
    "system_read_count": 0
}
"""

    @Language("JSON")
    private fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

    @Language("JSON")
    private fun inntektsmeldingMessage(hendelse: Hendelse) =
        """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

    private fun String.httpGet(expectedStatus: HttpStatusCode = HttpStatusCode.OK, testBlock: String.() -> Unit = {}) {
        val token = jwtStub.createTokenFor(
            subject = "fp_object_id",
            authorizedParty = "fp_object_id",
            audience = "spokelse_azure_ad_app_id"
        )

        val connection = appBaseUrl.handleRequest(HttpMethod.Get, this,
            builder = {
                setRequestProperty(HttpHeaders.Authorization, "Bearer $token")
            })

        assertEquals(expectedStatus.value, connection.responseCode)
        connection.responseBody.testBlock()
    }

    private fun String.handleRequest(
        method: HttpMethod,
        path: String,
        builder: HttpURLConnection.() -> Unit = {}
    ): HttpURLConnection {
        val url = URL("$this$path")
        val con = url.openConnection() as HttpURLConnection
        con.requestMethod = method.value

        con.builder()

        con.connectTimeout = 1000
        con.readTimeout = 5000
        con.readTimeout = 120000

        return con
    }

    private val HttpURLConnection.responseBody: String
        get() {
            val stream: InputStream? = if (responseCode in 200..299) {
                inputStream
            } else {
                errorStream
            }

            return stream?.use { it.bufferedReader().readText() } ?: ""
        }
}
