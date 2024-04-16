package no.nav.helse.spokelse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spokelse.ApplicationIdAllowlist.applicationId
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractE2ETest {
    private val env = mapOf(
        "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to "http://localhost",
        "AZURE_APP_CLIENT_ID" to "clientId",
        "AZURE_APP_CLIENT_SECRET" to "secret",
        "INFOTRYGD_URL" to "http://localhost",
        "INFOTRYGD_SCOPE" to "api://infotrygd"
    )
    private val auth = Auth.auth(
        name = "issuer",
        clientId = "spokelse_azure_ad_app_id",
        discoveryUrl = "${wireMockServer.baseUrl()}/config"
    )
    private val authorizationHeader = jwtStub.createTokenFor(
        subject = "fp_object_id",
        authorizedParty = "fp_object_id",
        audience = "spokelse_azure_ad_app_id"
    )

    protected lateinit var dataSource: DataSource
    protected lateinit var dokumentDao: DokumentDao
    protected lateinit var utbetaltDao: UtbetaltDao
    protected lateinit var gamleUtbetalingerDao: GamleUtbetalingerDao
    protected lateinit var lagreVedtakDao: LagreVedtakDao
    protected lateinit var tbdUtbetalingDao: TbdUtbetalingDao

    @BeforeAll
    fun setup() {
        PgDb.start()
        dataSource = PgDb.connection()
        dokumentDao = DokumentDao(dataSource)
        utbetaltDao = UtbetaltDao(dataSource)
        gamleUtbetalingerDao = GamleUtbetalingerDao(::dataSource)
        lagreVedtakDao = LagreVedtakDao(dataSource)
        tbdUtbetalingDao = TbdUtbetalingDao(::dataSource)
    }

    @BeforeEach
    protected fun reset() {
        PgDb.reset()
    }

    protected fun assertApiRequest(
        path: String,
        httpMethod: String = "GET",
        requestBody: String? = null,
        forventetHttpStatus: Int = 200,
        forventetResponseBody: String? = null,
        timeout: Int = 5,
        authorized: Boolean = true
    ) {
        testApplication {
            this.application {
                spokelse(env, auth, gamleUtbetalingerDao, TbdUtbetalingApi(tbdUtbetalingDao), object: ApiTilgangsstyring {
                    override fun utbetaltePerioder(call: ApplicationCall) { check(call.applicationId == "fp_object_id") }
                    override fun grunnlag(call: ApplicationCall) { check(call.applicationId == "fp_object_id") }
                })
            }

            Awaitility.await().atMost(timeout.toLong(), TimeUnit.SECONDS).untilAsserted {
                val response = runBlocking {
                    client.request("/$path") {
                        method = HttpMethod.parse(httpMethod)
                        accept(ContentType.Application.Json)
                        if (authorized) {
                            bearerAuth(authorizationHeader)
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
        private var jwtStub: JwtStub
        private val wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort()).apply { start() }

        init {
            jwtStub = JwtStub("Microsoft Azure AD", wireMockServer)
            WireMock.stubFor(jwtStub.stubbedJwkProvider())
            WireMock.stubFor(jwtStub.stubbedConfigProvider())

            Runtime.getRuntime().addShutdownHook(Thread {
                wireMockServer.stop()
            })
        }
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
