package no.nav.helse.spokelse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.helse.rapids_rivers.inMemoryRapid
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiTest {
    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private lateinit var jwtStub: JwtStub
    private lateinit var appBaseUrl: String
    private val objectmapper = jacksonObjectMapper()
    private val dataSource = testDataSource()

    private val dokumentDao = DokumentDao(dataSource)
    private val vedtakDao = VedtakDao(dataSource)

    private val sykmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Sykmelding)
    private val søknad = Hendelse(UUID.randomUUID(), sykmelding.hendelseId, Dokument.Søknad)
    private val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
    @BeforeAll
    fun setupEnv() {
        setupMockAuth()

        val randomPort = randomPort()
        val rapid = inMemoryRapid {
            ktor {
                port(randomPort)
                withCollectorRegistry(CollectorRegistry())
                module {
                    spokelse(
                        Environment.Auth(
                            name = "spokelse",
                            clientId = "client-Id",
                            validConsumers = listOf("arena"),
                            issuer = "Microsoft Azure AD",
                            jwksUri = "${wireMockServer.baseUrl()}/jwks"
                        ), dokumentDao, vedtakDao
                    )
                }
            }
        }.apply {
            start()
            NyttDokumentRiver(this, dokumentDao)
        }

        appBaseUrl = "http://localhost:$randomPort"

        rapid.sendToListeners(sendtSøknadMessage(sykmelding, søknad))
        rapid.sendToListeners(inntektsmeldingMessage(inntektsmelding))
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
    fun `tom respons uten params`() {
        "/dokumenter".httpGet(HttpStatusCode.OK) {
            assertEquals(this, "[]")
        }
    }

    @Test
    fun `finner dokumenter for en hendelseId`() {
        "/dokumenter?hendelseId=${sykmelding.hendelseId}".httpGet(HttpStatusCode.OK) {
            assertEquals(objectmapper.writeValueAsString(listOf(sykmelding, søknad)),this)
        }
    }

    @Test
    fun `finner dokumenter for flere hendelseIder`() {
        "/dokumenter?hendelseId=${sykmelding.hendelseId}&hendelseId=${inntektsmelding.hendelseId}&hendelseId=${søknad.hendelseId}".httpGet(HttpStatusCode.OK) {
            assertEquals(objectmapper.writeValueAsString(listOf(sykmelding, søknad, inntektsmelding)),this)
        }
    }

    private fun String.httpGet(expectedStatus: HttpStatusCode = HttpStatusCode.OK, testBlock: String.() -> Unit = {}) {
        val token = jwtStub.createTokenFor(
            audience = "client-Id",
            subject = "arena",
            authorizedParty = "arena"
        )

        val connection = appBaseUrl.handleRequest(
            HttpMethod.Get, this,
            builder = {
                setRequestProperty(HttpHeaders.Authorization, "Bearer $token")
            })

        assertEquals(expectedStatus.value, connection.responseCode)
        connection.responseBody.testBlock()
    }
}
