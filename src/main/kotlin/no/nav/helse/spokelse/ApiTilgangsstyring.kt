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

    internal fun gjenståendeSykedager(call: ApplicationCall) = call.harTilgangTil(endepunkt = "gjenstaende-sykedager", enAvRollene = setOf("aap-les"))

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

    private fun ApplicationCall.harTilgangTil(
        endepunkt: String,
        enAvRollene: Set<String>,
    ): Boolean {
        val rolle = enAvRollene.firstOrNull { it in roles }
        if (rolle == null) {
            loggError(
                "Applikasjon mangler rolle for endepunkt",
                "endepunkt" to "/$endepunkt",
                "applicationName" to applicationName,
                "applicationId" to applicationId,
                "påkrevdeRoller" to enAvRollene.toString(),
                "roller" to roles.toString(),
            )
            return false
        }

        loggInfo(
            "Håndterer request til /$endepunkt",
            "applicationName" to applicationName,
            "applicationId" to applicationId,
            "rolle" to rolle,
        )
        return true
    }

    private fun ApplicationCall.håndhevTilgangTil(
        endepunkt: String,
        enAvRollene: Set<String>,
    ) = check(harTilgangTil(endepunkt, enAvRollene)) {
        "Applikasjonen $applicationName ($applicationId) har ikke tilgang til /$endepunkt - Må ha en av rollene $enAvRollene, har bare $roles"
    }

    private fun ApplicationCall.håndhevTilgangTil(
        endepunkt: String,
        påkrevdRolle: String,
    ) = håndhevTilgangTil(endepunkt, setOf(påkrevdRolle))
}
