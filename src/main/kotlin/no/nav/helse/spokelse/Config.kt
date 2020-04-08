package no.nav.helse.spokelse

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spokelse.Environment.Auth.Companion.auth
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class Environment(
    val raw: Map<String, String>,
    val mq: MQ,
    val db: DB,
    val auth: Auth
) {
    constructor(raw: Map<String, String>) : this(
        raw = raw,
        mq = MQ(
            hostname = raw.getValue("MQ_HOSTNAME"),
            port = raw.getValue("MQ_PORT").toInt(),
            channel = raw.getValue("MQ_CHANNEL"),
            queueManager = raw.getValue("MQ_QUEUE_MANAGER"),
            username = raw.getValue("MQ_USERNAME"),
            password = raw.getValue("MQ_PASSWORD"),
            arenaOutput = raw.getValue("MQ_ARENA_OUTPUT"),
            arenaInputKvittering = raw.getValue("MQ_ARENA_INPUT_KVITTERING")
        ),
        db = DB(
            name = raw.getValue("DATABASE_NAME"),
            host = raw.getValue("DATABASE_HOST"),
            port = raw.getValue("DATABASE_PORT").toInt(),
            vaultMountPath = raw.getValue("DATABASE_VAULT_MOUNT_PATH")
        ),
        auth = auth(
            name = "ourissuer",
            clientId = "/var/run/secrets/nais.io/azure/client_id".readFile(),
            requiredGroup = raw.getValue("REQUIRED_GROUP"),
            discoveryUrl = raw.getValue("DISCOVERY_URL")
        )
    )

    class MQ(
        val hostname: String,
        val port: Int,
        val channel: String,
        val queueManager: String,
        val username: String,
        val password: String,
        val arenaOutput: String,
        val arenaInputKvittering: String
    )

    class DB(
        val name: String,
        val host: String,
        val port: Int,
        val vaultMountPath: String
    )

    class Auth(
        val name: String,
        val clientId: String,
        val requiredGroup: String,
        val issuer: String,
        jwksUri: String
    ) {
        val jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwksUri)).build()

        companion object {
            fun auth(name: String,
                     clientId: String,
                     requiredGroup: String,
                     discoveryUrl: String): Auth {
                val wellKnown = discoveryUrl.getJson()
                return Auth(
                    name = name,
                    clientId = clientId,
                    requiredGroup = requiredGroup,
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
}

private fun String.readFile() = File(this).readText(Charsets.UTF_8)
