package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class AzureClient(private val tenantUrl: String, private val clientId: String, private val clientSecret: String) {

    companion object {
        private val log = LoggerFactory.getLogger(AzureClient::class.java)
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = ObjectMapper()
    }

    private val tokencache: MutableMap<String, Token> = mutableMapOf()

    fun getToken(scope: String) =
            tokencache[scope]
                    ?.takeUnless(Token::isExpired)
                    ?: fetchToken(scope)
                            .also { token ->
                                tokencache[scope] = token
                            }


    private fun fetchToken(scope: String): Token {
        val (responseCode, responseBody) = with(URL("$tenantUrl/oauth2/v2.0/token").openConnection() as HttpURLConnection) {
            requestMethod = "POST"

            doOutput = true
            outputStream.use {
                it.bufferedWriter().apply {
                    write("client_id=$clientId&client_secret=$clientSecret&scope=$scope&grant_type=client_credentials")
                    flush()
                }
            }

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        tjenestekallLog.info("svar fra azure ad: responseCode=$responseCode responseBody=$responseBody")

        if (responseBody == null) {
            throw RuntimeException("ukjent feil fra azure ad (responseCode=$responseCode), responseBody er null")
        }

        val jsonNode = objectMapper.readTree(responseBody)

        if (jsonNode.has("error")) {
            log.error("${jsonNode["error_description"].textValue()}: $jsonNode")
            throw RuntimeException("error from the azure token endpoint: ${jsonNode["error_description"].textValue()}")
        } else if (responseCode >= 300) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from azure ad")
        }

        return Token(
                tokenType = jsonNode["token_type"].textValue(),
                expiresIn = jsonNode["expires_in"].longValue(),
                accessToken = jsonNode["access_token"].textValue()
        )
    }

    data class Token(val tokenType: String, val expiresIn: Long, val accessToken: String) {
        companion object {
            private const val leewaySeconds = 60
        }

        private val expiresOn = Instant.now().plusSeconds(expiresIn - leewaySeconds)

        fun isExpired(): Boolean = expiresOn.isBefore(Instant.now())
    }
}
