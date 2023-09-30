package no.nav.helse.spokelse

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.auth.jwt.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class Auth(
    private val name: String,
    private val clientId: String,
    private val issuer: String,
    jwksUri: String
) {
    private val jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwksUri)).build()

    fun configureVerification(configuration: JWTAuthenticationProvider.Config) {
        configuration.verifier(jwkProvider, issuer) {
            withAudience(clientId)
        }
        configuration.validate { credentials -> JWTPrincipal(credentials.payload) }
    }

    companion object {
        fun auth(
            name: String,
            clientId: String,
            discoveryUrl: String
        ): Auth {
            val wellKnown = discoveryUrl.getJson()
            return Auth(
                name = name,
                clientId = clientId,
                issuer = wellKnown["issuer"].textValue(),
                jwksUri = wellKnown["jwks_uri"].textValue()
            )
        }

        private fun String.getJson(): JsonNode {
            val (responseCode, responseBody) = this.fetchUrl()
            if (responseCode >= 300 || responseBody == null) throw RuntimeException("got status $responseCode from ${this}.")
            return jacksonObjectMapper().readTree(responseBody)
        }

        private fun String.fetchUrl() = with(URL(this).openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

    }
}
