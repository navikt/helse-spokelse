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
            env.configureVerification(this)
        }
    }
}
