package no.nav.helse.spokelse

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.slf4j.LoggerFactory

interface ApiTilgangsstyring {
    fun utbetaltePerioder(call: ApplicationCall)
    fun grunnlag(call: ApplicationCall)
}

internal object ApplicationIdAllowlist: ApiTilgangsstyring {
    override fun utbetaltePerioder(call: ApplicationCall) =
        call.håndhevTilgangTil("utbetalte-perioder", AllowlistUtbetaltePerioder)

    override fun grunnlag(call: ApplicationCall) {
        sikkerlogg.info("Håndterer request til /grunnlag fra Foreldrepenger/K9 (${call.applicationId})")
    }

    private val AllowlistUtbetaltePerioder = mapOf(
        "885a87c7-d4c4-4ace-8b1c-a8da50eb719c" to "spapi-dev",
        "11c3bc3d-1da7-4598-a8c4-73bead228a90" to "spapi-prod"
    )

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    internal val ApplicationCall.applicationId get() = this
        .principal<JWTPrincipal>()?.getClaim("azp", String::class)
        .takeUnless { it.isNullOrBlank() }
        ?: throw IllegalStateException("Mangler 'azp' claim i access token")

    private fun ApplicationCall.håndhevTilgangTil(endepunkt: String, allowlist: Map<String, String>) {
        val app = allowlist.getOrElse(applicationId) {
            "Applikasjonen $applicationId har ikke tilgang til /$endepunkt".let {
                sikkerlogg.error(it)
                throw IllegalStateException(it)
            }
        }
        sikkerlogg.info("Håndterer request til /$endepunkt fra $app ($applicationId)")
    }
}
