package no.nav.helse.spokelse

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt

internal fun Application.azureAdAppAuthentication(env: Environment.Auth) {
    install(Authentication) {
        jwt {
            verifier(env.jwkProvider, env.issuer)
            validate { credentials ->
                val authorizedParty: String?  = credentials.payload.getClaim("azp").asString()

                if (env.clientId !in credentials.payload.audience || authorizedParty !in env.validConsumers) {
                    log.info("${credentials.payload.subject} with audience ${credentials.payload.audience} and authorized party $authorizedParty is not authorized to use this app, denying access")
                    return@validate null
                }

                JWTPrincipal(credentials.payload)
            }
        }
    }
}
