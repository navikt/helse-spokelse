package no.nav.helse.spokelse

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

internal fun Application.azureAdAppAuthentication(env: Auth) {
    install(Authentication) {
        jwt {
            env.configureVerification(this)
        }
    }
}
