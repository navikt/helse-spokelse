package no.nav.helse.spokelse

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
import org.awaitility.Awaitility
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {
    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var hikariConfig: HikariConfig
    private lateinit var dataSource: HikariDataSource

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private lateinit var jwtStub: JwtStub

    private lateinit var vedtakDAO: VedtakDAO
    private lateinit var rapid: InMemoryRapid

    private lateinit var appBaseUrl: String

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

        val randomPort = ServerSocket(0).use { it.localPort }
        appBaseUrl = "http://localhost:$randomPort"

        rapid = inMemoryRapid {
            ktor {
                port(randomPort)
                module {
                    spokelse(auth(
                        name = "issuer",
                        clientId = "spokelse_azure_ad_app_id",
                        requiredGroup = "gruppe",
                        discoveryUrl = "${wireMockServer.baseUrl()}/config"
                    ))
                }
            }
        }.apply { start() }

        vedtakDAO = VedtakDAO(dataSource)

        VedtakRiver(rapid, vedtakDAO)
    }

    @Test
    fun `skriver vedtak til db`() {
        rapid.sendToListeners(
            """{
                  "fnr": "01010145678",
                  "vedtaksperiodeId": "e6e5fdaa-743c-4755-8b86-c03ef9c624a9",
                  "fom": "2020-04-01",
                  "tom": "2020-04-06",
                  "grad": 6.0
                }""")

        val vedtak = vedtakDAO.hentVedtak("01010145678")

        assertEquals("01010145678", vedtak?.fnr)
        assertEquals(UUID.fromString("e6e5fdaa-743c-4755-8b86-c03ef9c624a9"), vedtak?.vedtaksperiodeId)
        assertEquals(LocalDate.of(2020, 4, 1), vedtak?.fom)
        assertEquals(LocalDate.of(2020, 4, 6), vedtak?.tom)
        assertEquals(6.0, vedtak?.grad)

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted { "/grunnlag?fodselsnummer=12341234123".httpGet() }
    }

    private fun String.httpGet(expectedStatus: HttpStatusCode = HttpStatusCode.OK, testBlock: String.() -> Unit = {}) {
        val token = jwtStub.createTokenFor(
            subject = "en_saksbehandler_ident",
            groups = listOf("gruppe"),
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
