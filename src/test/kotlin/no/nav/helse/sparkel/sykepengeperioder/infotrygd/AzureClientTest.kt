package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AzureClientTest {

    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    private val azureClient: AzureClient

    init {
        azureClient = AzureClient(server.baseUrl(), "clientId", "clientSecret")
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun `henter bare et token ved to kall`() {

        WireMock.stubFor(WireMock.post("/oauth2/v2.0/token").willReturn(WireMock.okJson(tokenResponse)))

        val token1 = azureClient.getToken("scope")
        val token2 = azureClient.getToken("scope")

        assertEquals(token1, token2)
        WireMock.verify(1, postRequestedFor(urlEqualTo("/oauth2/v2.0/token")))
    }

    private val tokenResponse = """
{
    "token_type": "Bearer",
    "expires_in": 3599,
    "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP..."
}
                """
}
