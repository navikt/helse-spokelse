package no.nav.helse.spokelse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbtalingApi
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractE2ETest {
    private val auth = Auth.auth(
        name = "issuer",
        clientId = "spokelse_azure_ad_app_id",
        discoveryUrl = "${wireMockServer.baseUrl()}/config"
    )
    private val authorizationHeader = jwtStub.createTokenFor(
        subject = "fp_object_id",
        authorizedParty = "fp_object_id",
        audience = "spokelse_azure_ad_app_id"
    ).let { "Bearer $it" }

    protected lateinit var dataSource: DataSource
    protected lateinit var dokumentDao: DokumentDao
    protected lateinit var utbetaltDao: UtbetaltDao
    private lateinit var vedtakDao: HentVedtakDao
    protected lateinit var lagreVedtakDao: LagreVedtakDao
    private lateinit var annulleringDao: AnnulleringDao
    protected lateinit var tbdUtbetalingDao: TbdUtbetalingDao
    protected lateinit var rapid: TestRapid

    @BeforeAll
    fun setup() {
        PgDb.start()
        dataSource = PgDb.connection()
        dokumentDao = DokumentDao(dataSource)
        utbetaltDao = UtbetaltDao(dataSource)
        vedtakDao = HentVedtakDao(::dataSource)
        lagreVedtakDao = LagreVedtakDao(dataSource)
        annulleringDao = AnnulleringDao(::dataSource)
        tbdUtbetalingDao = TbdUtbetalingDao(::dataSource)

        rapid = TestRapid().apply {
            registerRivers(annulleringDao)
        }
    }

    @BeforeEach
    protected fun reset() {
        rapid.reset()
        PgDb.reset()
    }

    protected fun assertApiRequest(
        path: String,
        httpMethod: String = "GET",
        requestBody: String? = null,
        forventetHttpStatus: Int = 200,
        forventetResponseBody: String? = null,
        timeout: Int = 1,
        authorized: Boolean = true
    ) {
        withTestApplication({
            spokelse(auth, vedtakDao, TbdUtbtalingApi(tbdUtbetalingDao))
        }) {
            Awaitility.await().atMost(timeout.toLong(), TimeUnit.SECONDS).untilAsserted {
                handleRequest(HttpMethod.parse(httpMethod.uppercase()), "/$path") {
                    addHeader(HttpHeaders.Accept, "application/json")
                    if (authorized) {
                        addHeader(HttpHeaders.Authorization, authorizationHeader)
                    }
                    if (requestBody != null) {
                        setBody(requestBody)
                        addHeader(HttpHeaders.ContentType, "application/json")
                    }
                }.apply {
                    assertEquals(HttpStatusCode.fromValue(forventetHttpStatus), response.status())
                    forventetResponseBody?.let {
                        JSONAssert.assertEquals(it, response.content!!, true)
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
        internal fun nå() = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDateTime()

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
