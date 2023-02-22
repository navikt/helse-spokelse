package no.nav.helse.spokelse

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*

internal fun Application.azureAdAppAuthentication(env: Auth) {
    install(Authentication) {
        jwt {
            env.configureVerification(this)
        }
    }
}
