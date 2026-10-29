package no.nav.helse.spokelse

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo

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

    internal fun utbetaltePerioderPersonoversikt(call: ApplicationCall) {
        call.håndhevTilgangTil(endepunkt = "utbetalte-perioder-personoversikt", påkrevdRolle = "personoversikt-les")
    }

    internal fun grunnlag(call: ApplicationCall) {
        call.håndhevTilgangTil(endepunkt = "grunnlag", enAvRollene = setOf("foreldrepenger-les", "k9-les"))
    }

    internal fun harTilgangTilGjenståendeSykedager(call: ApplicationCall) =
        try {
            call.håndhevTilgangTil(endepunkt = "gjenstaende-sykedager", påkrevdRolle = "aap-les")
            true
        } catch (_: IllegalStateException) {
            false
        }

    private val ApplicationCall.applicationId get() =
        this
            .principal<JWTPrincipal>()
            ?.getClaim("azp", String::class)
            .takeUnless { it.isNullOrBlank() }
            ?: "n/a"

    private val ApplicationCall.applicationName get() =
        this
            .principal<JWTPrincipal>()
            ?.getClaim("azp_name", String::class)
            .takeUnless { it.isNullOrBlank() }
            ?: "n/a"

    private val ApplicationCall.roles get() =
        this
            .principal<JWTPrincipal>()
            ?.getListClaim("roles", String::class)
            ?: emptyList()

    private fun ApplicationCall.håndhevTilgangTil(
        endepunkt: String,
        enAvRollene: Set<String>,
    ) {
        val rolle =
            enAvRollene.firstOrNull { it in roles } ?: run {
                val feilmelding = "Applikasjonen $applicationName ($applicationId) har ikke tilgang til /$endepunkt - Må ha en av rollene $enAvRollene, har bare $roles"
                loggError(
                    "Applikasjon mangler rolle for endepunkt",
                    "endepunkt" to "/$endepunkt",
                    "applicationName" to applicationName,
                    "applicationId" to applicationId,
                    "påkrevdeRoller" to enAvRollene.toString(),
                    "roller" to roles.toString(),
                )
                throw IllegalStateException(feilmelding)
            }

        loggInfo(
            "Håndterer request til /$endepunkt",
            "applicationName" to applicationName,
            "applicationId" to applicationId,
            "rolle" to rolle,
        )
    }

    private fun ApplicationCall.håndhevTilgangTil(
        endepunkt: String,
        påkrevdRolle: String,
    ) = håndhevTilgangTil(endepunkt, setOf(påkrevdRolle))
}
