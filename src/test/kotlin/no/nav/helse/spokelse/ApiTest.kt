package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.JsonNode
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.KtorBuilder
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spokelse.Events.genererFagsystemId
import no.nav.helse.spokelse.Events.inntektsmeldingEvent
import no.nav.helse.spokelse.Events.sendtSøknadNavEvent
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbtalingApi
import org.awaitility.Awaitility
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.net.Socket
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiTest {
    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private lateinit var jwtStub: JwtStub
    private lateinit var appBaseUrl: String

    private val sykmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Sykmelding)
    private val søknad = Hendelse(UUID.randomUUID(), sykmelding.hendelseId, Dokument.Søknad)
    private val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
    private lateinit var rapid: TestRapid
    private lateinit var server: ApplicationEngine
    private lateinit var dataSource: DataSource

    private lateinit var dokumentDao: DokumentDao
    private lateinit var vedtakDao: HentVedtakDao
    private lateinit var lagreVedtakDao: LagreVedtakDao
    private lateinit var tbdUtbetalingDao: TbdUtbetalingDao
    private val client = HttpClient(Apache) {
        install(JsonFeature)
    }

    @AfterEach
    fun resetSchema() {
        PgDb.reset()
    }

    @BeforeAll
    fun setupEnv() {
        setupMockAuth()
        PgDb.start()

        dataSource = PgDb.connection()
        dokumentDao = DokumentDao(dataSource)
        vedtakDao = HentVedtakDao(dataSource)
        lagreVedtakDao = LagreVedtakDao(dataSource)
        tbdUtbetalingDao = TbdUtbetalingDao(dataSource)

        val randomPort = randomPort()
        rapid = TestRapid().apply {
            NyttDokumentRiver(this, dokumentDao)
        }
        server = KtorBuilder()
            .port(randomPort)
            .withCollectorRegistry(CollectorRegistry())
            .module {
                spokelse(
                    Environment.Auth(
                        name = "spokelse",
                        clientId = "client-Id",
                        issuer = "Microsoft Azure AD",
                        jwksUri = "${wireMockServer.baseUrl()}/jwks"
                    ), vedtakDao, TbdUtbtalingApi(emptyMap(), tbdUtbetalingDao)
                )
            }
            .build(CIO)
        server.start()

        appBaseUrl = "http://localhost:$randomPort"
    }

    @BeforeEach
    fun cleanupDatabase() {
        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        rapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))
    }

    private fun setupMockAuth() {
        //Stub ID provider (for authentication of REST endpoints)
        wireMockServer.start()
        Awaitility.await("vent på WireMockServer har startet")
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
    }

    @Test
    fun `finner sykepengeperioder for fnr`() = runBlocking {
        val fødselsnummer = "01010101010"

        lagreOldVedtak(fødselsnummer, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))

        val response = hentUtbetalinger(fødselsnummer)

        assertEquals(1, response.size())
    }

    @Test
    fun `har data for 1 person, spør om utbetalinger for to fødselsnumre`() {
        val fødselsnummer1 = "01010101010"
        val fødselsnummer2 = "01010101011"

        lagreOldVedtak(fødselsnummer1, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))

        val response = hentUtbetalinger(fødselsnummer1, fødselsnummer2)

        assertEquals(2, response.size())
        assertEquals(1, response.count { it.hasNonNull("refusjonstype") })
    }

    @Test
    fun `har data for 2 personer, spør om utbetalinger for 3 fødselsnumre`() {
        val fødselsnummer1 = "01010101010"
        val fødselsnummer2 = "01010101011"
        val fødselsnummer3 = "01010101012"

        lagreOldVedtak(fødselsnummer1, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))
        lagreOldVedtak(fødselsnummer3, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))

        val response = hentUtbetalinger(fødselsnummer1, fødselsnummer2, fødselsnummer3)

        assertEquals(3, response.size())
        assertEquals(2, response.count { it.hasNonNull("refusjonstype") })
    }

    fun hentUtbetalinger(vararg fødselsnumre: String) = runBlocking {
        client.post<JsonNode>("$appBaseUrl/utbetalinger") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${createToken()}")
            body = fødselsnumre
        }
    }

    fun lagreOldVedtak(
        fødselsnummer: String,
        fagsystemId: String = genererFagsystemId(),
        perioder: List<ClosedRange<LocalDate>>
    ) {
        val vedtaksperiodeId = UUID.randomUUID()
        val orgnummer = "98765432"
        lagreVedtakDao.save(
            OldVedtak(
                vedtaksperiodeId, fødselsnummer, orgnummer, perioder.map {
                    OldUtbetaling(
                        it.start,
                        it.endInclusive,
                        100.0,
                        1337,
                        1337,
                        9001
                    )
                }, LocalDateTime.now(), 10, 238,
                Dokumenter(
                    Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Sykmelding),
                    Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Søknad),
                    null
                )
            )
        )
        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)
    }

    fun createToken() = jwtStub.createTokenFor(
        audience = "client-Id",
        subject = "arena",
        authorizedParty = "arena"
    )
}
