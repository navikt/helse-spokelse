package no.nav.helse.spokelse.gjenstaendesykedager

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spokelse.Tilgangsstyring
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

private val logger = navngittLogger("no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerApi")
private val objectMapper = jacksonObjectMapper()

internal enum class Feilkode {
    UGYLDIG_FORESPØRSEL,
}

internal data class Feil(
    val feilkode: Feilkode,
    val melding: String,
)

internal enum class Status {
    VEDTAK_I_NY_LØSNING,
    SISTE_UTBETALING_I_INFOTRYGD,
    INGEN_VEDTAK,
}

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class GjenståendeSykedagerResponse(
    val status: Status,
    val gjenståendeDager: Int? = null,
    val vurdertTilOgMed: LocalDate? = null,
)

internal fun Route.gjenståendeSykedagerApi(
    gjenståendeSykedager: GjenståendeSykedager,
    meterRegistry: MeterRegistry,
) {
    fun tellUtfall(status: Status?) = meterRegistry.counter("gjenstaende_sykedager_oppslag", "utfall", status?.name ?: "FEIL").increment()

    post("/gjenstaende-sykedager") {
        if (!Tilgangsstyring.gjenståendeSykedager(call)) return@post call.respond(HttpStatusCode.Forbidden)

        val personidentifikator =
            personidentifikatorFraRequest()
                ?: return@post respondFeil(HttpStatusCode.BadRequest, Feilkode.UGYLDIG_FORESPØRSEL, "Forventer en body med fødselsnummer på 11 siffer")

        val response =
            try {
                gjenståendeSykedager.hent(personidentifikator).tilResponse()
            } catch (exception: Exception) {
                tellUtfall(null)
                throw exception
            }

        logger.info(
            "Svarer på /gjenstaende-sykedager",
            "fødselsnummer" to personidentifikator.toString(),
            "status" to response.status.name,
            "gjenståendeDager" to response.gjenståendeDager?.toString(),
            "vurdertTilOgMed" to response.vurdertTilOgMed?.toString(),
        )
        tellUtfall(response.status)
        call.respond(HttpStatusCode.OK, response)
    }
}

internal fun Route.gjenståendeSykedagerDokumentasjon() {
    get("/$OPENAPI_FIL") {
        call.respondText(openApi, ContentType.parse("application/yaml"))
    }
    swaggerUI(path = "swagger", swaggerFile = OPENAPI_FIL)
}

private const val OPENAPI_FIL = "openapi/gjenstaende-sykedager.yml"
private val openApi =
    requireNotNull(object {}.javaClass.getResource("/$OPENAPI_FIL")) { "Fant ikke OpenAPI-specen" }.readText()


private fun GjenståendeSykedager.Resultat.tilResponse() =
    when (this) {
        is GjenståendeSykedager.Resultat.VedtakINyLøsning -> GjenståendeSykedagerResponse(status, gjenståendeDager, vurdertTilOgMed)
        GjenståendeSykedager.Resultat.IngenVedtak,
        GjenståendeSykedager.Resultat.SisteUtbetalingIInfotrygd,
            -> GjenståendeSykedagerResponse(status)
    }

private suspend fun RoutingContext.personidentifikatorFraRequest(): Personidentifikator? {
    val body = runCatching { objectMapper.readTree(call.receiveText()) }.getOrNull() ?: return null
    val fødselsnummer = body.path("fødselsnummer").takeIf { it.isString }?.asString() ?: return null
    return runCatching { Personidentifikator(fødselsnummer) }.getOrNull()
}

private suspend fun RoutingContext.respondFeil(
    status: HttpStatusCode,
    feilkode: Feilkode,
    melding: String,
) = call.respond(status, Feil(feilkode, melding))
