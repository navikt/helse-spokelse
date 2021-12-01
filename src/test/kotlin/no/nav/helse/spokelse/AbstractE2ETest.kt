package no.nav.helse.spokelse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.awaitility.Awaitility
import org.flywaydb.core.Flyway
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*

internal abstract class AbstractE2ETest {
    private val dokumentDao = DokumentDao(dataSource)
    private val utbetaltDao = UtbetaltDao(dataSource)
    private val vedtakDao = VedtakDao(dataSource)
    private val annulleringDao = AnnulleringDao(dataSource)
    private val auth = Environment.Auth.auth(
        name = "issuer",
        clientId = "spokelse_azure_ad_app_id",
        discoveryUrl = "${wireMockServer.baseUrl()}/config"
    )
    private val authorizationHeader = jwtStub.createTokenFor(
        subject = "fp_object_id",
        authorizedParty = "fp_object_id",
        audience = "spokelse_azure_ad_app_id"
    ).let { "Bearer $it" }

    protected val rapid = TestRapid().apply {
        registerRivers(dokumentDao, utbetaltDao, vedtakDao, annulleringDao)
    }

    protected fun reset() {
        rapid.reset()
        dataSource.cleanDatabase()
        dataSource.migrateDatabase()
    }

    protected fun assertApiRequest(
        path: String,
        httpMethod: String = "GET",
        requestBody: String? = null,
        forventetHttpStatus: Int = 200,
        forventetResponseBody: String,
        timeout: Int = 5,
        authorized: Boolean = true
    ) {
        withTestApplication({
            spokelse(auth, dokumentDao, vedtakDao)
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
                    JSONAssert.assertEquals(forventetResponseBody, response.content!!, true)
                }
            }
        }
    }

    protected fun nyeDokumenter(): Triple<Hendelse, Hendelse, Hendelse> {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        return Triple(sykmelding, søknad, inntektsmelding)
    }

    internal companion object {
        private val embeddedPostgres = setupPostgres()
        private val dataSource = testDataSource(embeddedPostgres)
        private var jwtStub: JwtStub
        private val wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort()).apply { start() }

        init {
            jwtStub = JwtStub("Microsoft Azure AD", wireMockServer)
            WireMock.stubFor(jwtStub.stubbedJwkProvider())
            WireMock.stubFor(jwtStub.stubbedConfigProvider())

            Runtime.getRuntime().addShutdownHook(Thread {
                embeddedPostgres.close()
                wireMockServer.stop()
            })
        }

        private fun setupPostgres() = EmbeddedPostgres.builder().setPort(56789).start()

        private fun testDataSource(embeddedPostgres: EmbeddedPostgres): DataSource {
            val hikariConfig = HikariConfig().apply {
                this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
            }
            return HikariDataSource(hikariConfig).apply { migrateDatabase() }
        }

        private fun DataSource.migrateDatabase() {
            Flyway
                .configure()
                .dataSource(this)
                .load().also { it.migrate() }
        }

        private fun DataSource.cleanDatabase() {
            Flyway
                .configure()
                .dataSource(this)
                .load().also { it.clean() }
        }
    }
}
