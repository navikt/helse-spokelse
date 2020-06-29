package no.nav.helse.spokelse

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class JwtStub(private val issuer: String, private val wireMockServer: WireMockServer) {

    private val privateKey: RSAPrivateKey
    private val publicKey: RSAPublicKey

    init {
        val client = WireMock.create().port(wireMockServer.port()).build()
        WireMock.configureFor(client)

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(512)

        val keyPair = keyPairGenerator.genKeyPair()
        privateKey = keyPair.private as RSAPrivateKey
        publicKey = keyPair.public as RSAPublicKey
    }

    fun createTokenFor(subject: String, audience: String, authorizedParty: String): String {
        val algorithm = Algorithm.RSA256(publicKey, privateKey)

        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withKeyId("key-1234")
            .withSubject(subject)
            .withClaim("azp", authorizedParty)
            .sign(algorithm)
    }

    fun stubbedJwkProvider() = WireMock.get(WireMock.urlPathEqualTo("/jwks")).willReturn(
        WireMock.okJson(
            """
{
    "keys": [
        {
            "kty": "RSA",
            "alg": "RS256",
            "kid": "key-1234",
            "e": "${String(Base64.getEncoder().encode(publicKey.publicExponent.toByteArray()))}",
            "n": "${String(Base64.getEncoder().encode(publicKey.modulus.toByteArray()))}"
        }
    ]
}
"""
        )
    )

    fun stubbedConfigProvider() = WireMock.get(WireMock.urlPathEqualTo("/config")).willReturn(
        WireMock.okJson(
            """
{
    "jwks_uri": "${wireMockServer.baseUrl()}/jwks",
    "token_endpoint": "${wireMockServer.baseUrl()}/token",
    "issuer": "$issuer"
}
"""
        )
    )
}
