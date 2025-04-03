package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.naisful.test.naisfulTestApp
import com.github.navikt.tbd_libs.signed_jwt_issuer_test.Issuer
import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
internal abstract class AbstractE2ETest {
    private val env = mapOf(
        "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to "http://localhost",
        "AZURE_APP_CLIENT_ID" to "clientId",
        "AZURE_APP_CLIENT_SECRET" to "secret",
        "INFOTRYGD_URL" to "http://localhost",
        "INFOTRYGD_SCOPE" to "api://infotrygd"
    )

    private lateinit var testDataSource: TestDataSource
    protected val dataSource: DataSource get() = testDataSource.ds
    protected lateinit var dokumentDao: DokumentDao
    protected lateinit var utbetaltDao: UtbetaltDao
    protected lateinit var gamleUtbetalingerDao: GamleUtbetalingerDao
    protected lateinit var lagreVedtakDao: LagreVedtakDao
    protected lateinit var tbdUtbetalingDao: TbdUtbetalingDao

    private val issuer = Issuer("spkelse_issuer", "spokelse_azure_ad_app_id")
    private val auth by lazy { Auth.auth(
        name = "spokelse_issuer",
        clientId = "spokelse_azure_ad_app_id",
        discoveryUrl = issuer.wellKnownUri().toString()
    ) }

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
        app: String?
    ) {
        naisfulTestApp(
            testApplicationModule = {
                spokelse(env, auth, gamleUtbetalingerDao, TbdUtbetalingApi(tbdUtbetalingDao))
            },
            objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
            meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
        ) {
            Awaitility.await().atMost(timeout).untilAsserted {
                val response = runBlocking {
                    client.request("/$path") {
                        method = HttpMethod.parse(httpMethod)
                        accept(ContentType.Application.Json)
                        if (rolle != null || app != null) {
                            bearerAuth(issuer.accessToken {
                                app?.let { withClaim("azp", it) }
                                rolle?.let { withArrayClaim("roles", arrayOf(it)) }
                            })
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
                logg.info("fikk <{}> tilbake fra /{}", body, path)
                assertEquals(HttpStatusCode.fromValue(forventetHttpStatus), response.status)
                forventetResponseBody?.let {
                    try {
                        JSONAssert.assertEquals(it, body, true)
                    } catch (err: AssertionError) {
                        logg.info("<{}> er ikke som forventet <{}>", body, it, err)
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
        private val logg = LoggerFactory.getLogger(AbstractE2ETest::class.java)

        internal fun oppdrag(fødselsnummer: String, fagsystemId: String, fagområde: String, fom: LocalDate, tom: LocalDate) = Vedtak.Oppdrag(
            mottaker = fødselsnummer,
            fagområde = fagområde,
            fagsystemId = fagsystemId,
            totalbeløp = 0,
            utbetalingslinjer = listOf(
                Vedtak.Oppdrag.Utbetalingslinje(
                    fom = fom,
                    tom = tom,
                    dagsats = 123,
                    beløp = 321,
                    grad = 70.0,
                    sykedager = 248
                )
            ),
        )
    }
}
