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
import java.time.LocalDateTime
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

        vedtakDAO = VedtakDAO(dataSource)

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
                        discoveryUrl = "${wireMockServer.baseUrl()}/config"
                    ), vedtakDAO)
                }
            }
        }.apply { start() }


        VedtakRiver(rapid, vedtakDAO)
    }

    @Test
    fun `skriver vedtak til db`() {
        val fnr = "01010145678"

        val fom1 = LocalDate.of(2020, 4, 1)
        val tom1 = LocalDate.of(2020, 4, 6)
        val grad1 = 50.0
        rapid.sendToListeners(opprettVedtakUtenUtbetalingsreferanse(fnr, fom1, tom1, grad1))

        val fom2 = LocalDate.of(2020, 4, 10)
        val tom2 = LocalDate.of(2020, 4, 16)
        val grad2 = 70.0
        rapid.sendToListeners(opprettVedtakUtenUtbetalingsreferanse(fnr, fom2, tom2, grad2))

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            "/grunnlag?fodselsnummer=$fnr".httpGet {
                objectMapper.readValue<List<FpVedtak>>(this).apply {
                    assertEquals(2, size)

                    assertEquals(fom1, this[0].vedtaksreferanse)
                    assertEquals(LocalDateTime.of(2020, 4, 11, 10, 0), this[0].vedtattTidspunkt)
                    assertEquals(fom1, this[0].utbetalinger[0].fom)
                    assertEquals(tom1, this[0].utbetalinger[0].tom)
                    assertEquals(grad1, this[0].utbetalinger[0].grad)

                    assertEquals(fom2, this[1].vedtaksreferanse)
                    assertEquals(LocalDateTime.of(2020, 4, 11, 10, 0), this[1].vedtattTidspunkt)
                    assertEquals(fom2, this[1].utbetalinger[0].fom)
                    assertEquals(tom2, this[1].utbetalinger[0].tom)
                    assertEquals(grad2, this[1].utbetalinger[0].grad)
                }
            }
        }
    }

    @Test
    fun `støtter utbetalt-event på to formater`() {
        val fnr = "01010145679"

        val fom1 = LocalDate.of(2020, 4, 1)
        val tom1 = LocalDate.of(2020, 4, 6)
        val grad1 = 50.0
        rapid.sendToListeners(opprettVedtakMedUtbetalingsreferanse(fnr, fom1, tom1, grad1))

        val fom2 = LocalDate.of(2020, 4, 10)
        val tom2 = LocalDate.of(2020, 4, 16)
        val grad2 = 70.0
        rapid.sendToListeners(opprettVedtakUtenUtbetalingsreferanse(fnr, fom2, tom2, grad2))

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            "/grunnlag?fodselsnummer=$fnr".httpGet {
                objectMapper.readValue<List<FpVedtak>>(this).apply {
                    assertEquals(2, size)

                    assertEquals(fom1, this[0].vedtaksreferanse)
                    assertEquals(LocalDateTime.of(2020, 4, 11, 10, 0), this[0].vedtattTidspunkt)
                    assertEquals(fom1, this[0].utbetalinger[0].fom)
                    assertEquals(tom1, this[0].utbetalinger[0].tom)
                    assertEquals(grad1, this[0].utbetalinger[0].grad)

                    assertEquals(fom2, this[1].vedtaksreferanse)
                    assertEquals(LocalDateTime.of(2020, 4, 11, 10, 0), this[1].vedtattTidspunkt)
                    assertEquals(fom2, this[1].utbetalinger[0].fom)
                    assertEquals(tom2, this[1].utbetalinger[0].tom)
                    assertEquals(grad2, this[1].utbetalinger[0].grad)
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

    private fun opprettVedtakUtenUtbetalingsreferanse(fnr: String, fom: LocalDate, tom: LocalDate, grad: Double) =
        """{
              "@event_name": "utbetalt",
              "aktørId": "aktørId",
              "fødselsnummer": "$fnr",
              "førsteFraværsdag": "$fom",
              "utbetalingslinjer": [
                {
                  "fom": "$fom",
                  "tom": "$tom",
                  "dagsats": 1000,
                  "grad": $grad
                }
              ],
              "forbrukteSykedager": 123,
              "opprettet": "2020-04-11T10:00:00.00000",
              "system_read_count": 0
            }"""

    private fun opprettVedtakMedUtbetalingsreferanse(fnr: String, fom: LocalDate, tom: LocalDate, grad: Double) =
        """{
              "@event_name": "utbetalt",
              "aktørId": "aktørId",
              "fødselsnummer": "$fnr",
              "førsteFraværsdag": "$fom",
              "utbetaling": [
                {
                  "utbetalingsreferanse": "WKOZJT3JYNB3VNT5CE5U54R3Y4",
                  "utbetalingslinjer": [
                    {
                      "fom": "$fom",
                      "tom": "$tom",
                      "dagsats": 1000,
                      "grad": $grad
                    }
                  ]
                }
              ],
              "forbrukteSykedager": 123,
              "opprettet": "2020-04-11T10:00:00.00000",
              "system_read_count": 0
            }"""

    private fun String.httpGet(expectedStatus: HttpStatusCode = HttpStatusCode.OK, testBlock: String.() -> Unit = {}) {
        val token = jwtStub.createTokenFor(
            subject = "en_saksbehandler_ident",
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
