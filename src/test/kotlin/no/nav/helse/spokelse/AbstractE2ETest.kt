package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.naisful.test.naisfulTestApp
import com.github.navikt.tbd_libs.signed_jwt_issuer_test.Issuer
import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedager
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.utbetalteperioder.AccessToken
import no.nav.helse.spokelse.utbetalteperioder.Infotrygd
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioder
import no.nav.sykepenger.libs.logging.navngittLogger
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.skyscreamer.jsonassert.JSONAssert
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

@TestInstance(PER_CLASS)
internal abstract class AbstractE2ETest {
    // Svaret fra den falske Infotrygd. null gir 500.
    protected var infotrygdsvar: String? = """{"utbetaltePerioder": []}"""
    private val infotrygd =
        Infotrygd(
            httpClient =
                HttpClient(
                    MockEngine {
                        infotrygdsvar?.let { svar -> respond(svar, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
                            ?: respondError(HttpStatusCode.InternalServerError)
                    },
                ),
            scope = "api://infotrygd",
            accessToken =
                object : AccessToken() {
                    override suspend fun hentNytt(scope: String) = "token" to 3600L
                },
            url = "http://infotrygd",
        )

    private lateinit var testDataSource: TestDataSource
    protected val dataSource: DataSource get() = testDataSource.ds
    protected lateinit var dokumentDao: DokumentDao
    protected lateinit var utbetaltDao: UtbetaltDao
    protected lateinit var gamleUtbetalingerDao: GamleUtbetalingerDao
    protected lateinit var lagreVedtakDao: LagreVedtakDao
    protected lateinit var tbdUtbetalingDao: TbdUtbetalingDao
    protected lateinit var gjenståendeSykedagerDao: GjenståendeSykedagerDao

    private val issuer = Issuer("spkelse_issuer", "spokelse_azure_ad_app_id")
    private val auth by lazy {
        Auth.auth(
            name = "spokelse_issuer",
            clientId = "spokelse_azure_ad_app_id",
            discoveryUrl = issuer.wellKnownUri().toString(),
        )
    }

    @BeforeAll
    fun beforeAll() {
        issuer.start()
    }

    @AfterAll
    fun afterAll() {
        issuer.stop()
    }

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
        dokumentDao = DokumentDao(dataSource)
        utbetaltDao = UtbetaltDao(dataSource)
        gamleUtbetalingerDao = GamleUtbetalingerDao(::dataSource)
        lagreVedtakDao = LagreVedtakDao(dataSource)
        tbdUtbetalingDao = TbdUtbetalingDao(::dataSource)
        gjenståendeSykedagerDao = GjenståendeSykedagerDao(::dataSource)
        infotrygdsvar = """{"utbetaltePerioder": []}"""
    }

    @AfterEach
    protected fun reset() {
        databaseContainer.droppTilkobling(testDataSource)
    }

    protected fun assertApiRequest(
        path: String,
        httpMethod: String = "POST",
        requestBody: String? = null,
        forventetHttpStatus: Int = 200,
        forventetResponseBody: String? = null,
        timeout: Duration = Duration.ofSeconds(5),
        rolle: String?,
        app: String?,
    ) {
        naisfulTestApp(
            testApplicationModule = {
                val tbdUtbetalingApi = TbdUtbetalingApi(tbdUtbetalingDao)
                spokelse(
                    auth = auth,
                    gamleUtbetalingerDao = gamleUtbetalingerDao,
                    tbdUtbetalingApi = tbdUtbetalingApi,
                    utbetaltePerioder =
                        UtbetaltePerioder(
                            infotrygd = infotrygd,
                            tbdUtbetalingApi = tbdUtbetalingApi,
                            gamleUtbetalingerDao = gamleUtbetalingerDao,
                        ),
                    gjenståendeSykedager =
                        GjenståendeSykedager(
                            dao = gjenståendeSykedagerDao,
                            infotrygd = infotrygd,
                        ),
                )
            },
            objectMapper = jacksonObjectMapper(),
            meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
        ) {
            Awaitility.await().atMost(timeout).untilAsserted {
                val response =
                    runBlocking {
                        client.request("/$path") {
                            method = HttpMethod.parse(httpMethod)
                            accept(ContentType.Application.Json)
                            if (rolle != null || app != null) {
                                bearerAuth(
                                    issuer.accessToken {
                                        app?.let { withClaim("azp", it) }
                                        rolle?.let { withArrayClaim("roles", arrayOf(it)) }
                                    },
                                )
                            }
                            if (requestBody != null) {
                                contentType(ContentType.Application.Json)
                            }
                            if (requestBody != null) {
                                setBody(requestBody)
                            }
                        }
                    }
                val body = runBlocking { response.bodyAsText() }
                logg.info("Fikk svar tilbake fra /$path", "body" to body)
                assertEquals(HttpStatusCode.fromValue(forventetHttpStatus), response.status)
                forventetResponseBody?.let {
                    try {
                        JSONAssert.assertEquals(it, body, true)
                    } catch (err: AssertionError) {
                        logg.info("Svaret er ikke som forventet", err, "body" to body, "forventet" to it)
                        throw err
                    }
                }
            }
        }
    }

    protected fun nyeDokumenter(): Triple<Hendelse, Hendelse, Hendelse> {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        dokumentDao.opprett(sykmelding)
        dokumentDao.opprett(søknad)
        dokumentDao.opprett(inntektsmelding)
        return Triple(sykmelding, søknad, inntektsmelding)
    }

    internal companion object {
        private val logg = navngittLogger(AbstractE2ETest::class.java.name)

        internal fun oppdrag(
            fødselsnummer: String,
            fagsystemId: String,
            fagområde: String,
            fom: LocalDate,
            tom: LocalDate,
        ) = Vedtak.Oppdrag(
            mottaker = fødselsnummer,
            fagområde = fagområde,
            fagsystemId = fagsystemId,
            totalbeløp = 0,
            utbetalingslinjer =
                listOf(
                    Vedtak.Oppdrag.Utbetalingslinje(
                        fom = fom,
                        tom = tom,
                        dagsats = 123,
                        beløp = 321,
                        grad = 70.0,
                        sykedager = 248,
                    ),
                ),
        )
    }
}
