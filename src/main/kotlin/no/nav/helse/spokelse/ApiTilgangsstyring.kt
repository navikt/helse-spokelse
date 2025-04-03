package no.nav.helse.spokelse

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.slf4j.LoggerFactory

internal object Tilgangsstyring {
    internal fun utbetaltePerioder(call: ApplicationCall) {
        call.håndhevTilgangTil(endepunkt = "utbetalte-perioder", påkrevdRolle = "spleiselaget-les")
    }

    internal fun utbetaltePerioderAap(call: ApplicationCall) {
        call.håndhevTilgangTil(endepunkt = "utbetalte-perioder-aap", påkrevdRolle = "aap-les")
    }

    internal fun utbetaltePerioderDagpenger(call: ApplicationCall) {
        call.håndhevTilgangTil(endepunkt = "utbetalte-perioder-dagpenger", påkrevdRolle = "dagpenger-les")
    }

    internal fun grunnlag(call: ApplicationCall) {
        call.håndhevTilgangTil(endepunkt = "grunnlag", enAvRollene = setOf("foreldrepenger-les", "k9-les"))
    }

    private val ApplicationCall.applicationId get() = this
        .principal<JWTPrincipal>()?.getClaim("azp", String::class)
        .takeUnless { it.isNullOrBlank() }
        ?: throw IllegalStateException("Mangler 'azp' claim i access token")

    private val ApplicationCall.roles get() = this
        .principal<JWTPrincipal>()?.getListClaim("roles", String::class)
        ?: emptyList()

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    private fun ApplicationCall.håndhevTilgangTil(endepunkt: String, enAvRollene: Set<String>) {
        val rolle = enAvRollene.firstOrNull { it in roles } ?: run {
            val feilmelding = "Applikasjonen $applicationId har ikke tilgang til /$endepunkt - Må ha en av rollene $enAvRollene, har bare $roles"
            sikkerlogg.error(feilmelding)
            throw IllegalStateException(feilmelding)
        }

        sikkerlogg.info("Håndterer request til /$endepunkt fra $applicationId som har rolle $rolle")
    }

    private fun ApplicationCall.håndhevTilgangTil(endepunkt: String, påkrevdRolle: String) = håndhevTilgangTil(endepunkt, setOf(påkrevdRolle))
}
