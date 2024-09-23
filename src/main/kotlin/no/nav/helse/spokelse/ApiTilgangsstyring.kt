package no.nav.helse.spokelse

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.slf4j.LoggerFactory

interface ApiTilgangsstyring {
    fun utbetaltePerioder(call: ApplicationCall)
    fun utbetaltePerioderAap(call: ApplicationCall)
    fun grunnlag(call: ApplicationCall)
}

internal object ApplicationIdAllowlist: ApiTilgangsstyring {
    override fun utbetaltePerioder(call: ApplicationCall) =
        call.håndhevTilgangTil("utbetalte-perioder", AllowlistUtbetaltePerioder)

    override fun utbetaltePerioderAap(call: ApplicationCall) {
        call.håndhevTilgangTil("utbetalte-perioder-aap", AllowlistUtbetaltePerioderAap)
    }
    override fun grunnlag(call: ApplicationCall) {
        call.håndhevTilgangTil("grunnlag", AllowlistGrunnlag)
    }

    private val AllowlistUtbetaltePerioder = mapOf(
        "885a87c7-d4c4-4ace-8b1c-a8da50eb719c" to "spapi-dev",
        "11c3bc3d-1da7-4598-a8c4-73bead228a90" to "spapi-prod"
    )

    private val AllowlistUtbetaltePerioderAap = mapOf(
        "5b4656d2-e8f0-4df1-9e10-269133df697f" to "behandlingsflyt-dev"
    )

    private val AllowlistGrunnlag = mapOf(
        "7533b639-ec49-4531-80fd-752f412c3b5a" to "k9-abakus-prod",
        "0fc8e157-984d-46f8-911a-52f2b229fef9" to "fpabakus-prod",
        "17f6b413-aba3-46be-bdde-793ad7081e28" to "fpsak-prod",
        "6b779173-1246-41db-9f04-da4294a39a6c" to "fprisk-prod",
        "650e72f0-19a1-40ea-849f-8a3e986dbce9" to "k9-abakus-dev",
        "cb082ea5-4eec-4a1e-8bab-cf04181adaeb" to "fpabakus-dev",
        "11815622-aad9-49b5-997e-5d3c5b9c12f7" to "fpsak-dev",
        "dbaac815-e57c-41bf-9674-125b76e567c8" to "fprisk-dev",
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
