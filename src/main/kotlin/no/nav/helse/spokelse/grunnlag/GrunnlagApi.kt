package no.nav.helse.spokelse.grunnlag

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import no.nav.helse.spokelse.Tags
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.postMedRolle
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.treeToValue
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

private val logger = navngittLogger("no.nav.helse.spokelse.grunnlag.GrunnlagApi")

private val objectMapper = jacksonObjectMapper()

internal fun Route.grunnlagApi(
    gamleUtbetalingerDao: GamleUtbetalingerDao,
    tbdUtbetalingApi: TbdUtbetalingApi,
) {
    postMedRolle("/grunnlag", "foreldrepenger-les", "k9-les", dokumentasjon = { grunnlagDokumentasjon() }) {
        val json = objectMapper.readTree(call.receiveText())
        val request = if (json.isObject) objectMapper.treeToValue<ApiGrunnlagRequest>(json) else ApiGrunnlagRequest()
        when (val validering = request.valider()) {
            is UgyldigGrunnlagRequest -> {
                if (validering == UgyldigGrunnlagRequest.MANGLER_FØDSELSNUMMER) {
                    logger.error("/grunnlag Mangler fodselsnummer i request body")
                }
                call.respond(HttpStatusCode.BadRequest, validering.feilmelding)
            }

            is GyldigGrunnlagRequest -> {
                val time =
                    measureTimeMillis {
                        try {
                            val vedtak: List<FpVedtak> =
                                gamleUtbetalingerDao
                                    .hentUtbetalinger(validering.fødselsnummer, validering.fom)
                                    .somFpVedtak() +
                                    tbdUtbetalingApi
                                        .utbetalinger(
                                            validering.fødselsnummer,
                                            validering.fom,
                                            null,
                                        ).somFpVedtak()
                            call.respond<List<ApiGrunnlagVedtak>>(HttpStatusCode.OK, vedtak.tilApiGrunnlagVedtak())
                        } catch (e: Exception) {
                            if (e is CancellationException) {
                                currentCoroutineContext().ensureActive()
                            }
                            logger.error("Feil ved henting av vedtak", e, "fødselsnummer" to validering.fødselsnummer)
                            call.respond<String>(HttpStatusCode.InternalServerError, FEIL_VED_OPPSLAG)
                        }
                    }
                logger.info(
                    "Hentet vedtak for /grunnlag ($time ms)",
                    "fødselsnummer" to validering.fødselsnummer,
                    "fom" to validering.fom?.toString(),
                )
            }
        }
    }
}

internal const val HENT_GRUNNLAG = "hentGrunnlag"

private const val FEIL_VED_OPPSLAG = "Feil ved henting av vedtak"

private fun RouteConfig.grunnlagDokumentasjon() {
    tags(Tags.GRUNNLAG)
    operationId = HENT_GRUNNLAG
    summary = "Hent vedtak med utbetalinger"
    description =
        """
        Gir vedtakene med utbetalinger i Spleis for en person, eventuelt bare de med utbetalinger fra og med `fom`.

        Konsumenter: Foreldrepenger (`fpsak`, `fprisk` og `fpabakus`) og K9 (`k9-abakus`).

        Hvert oppdrag er et eget vedtak, så utbetalinger til arbeidsgiver og til personen kommer hver for seg.
        Svaret på `400`, og på `500` når oppslaget feiler, er ren tekst. Andre feil kommer som `application/problem+json`.
        """.trimIndent()
    request {
        body<ApiGrunnlagRequest> {
            required = true
            example("utenFom") {
                summary = "Alle vedtakene"
                value = mapOf("fodselsnummer" to "12345678911")
            }
            example("medFom") {
                summary = "Bare vedtak med utbetalinger fra og med `fom`"
                value = ApiGrunnlagRequest("12345678911", "2024-01-01")
            }
        }
    }
    response {
        code(HttpStatusCode.OK) {
            description = "Oppslaget gikk bra. Listen er tom når personen ikke har vedtak med utbetalinger."
            body<List<ApiGrunnlagVedtak>> {
                example("ettVedtak") {
                    value =
                        listOf(
                            ApiGrunnlagVedtak(
                                vedtaksreferanse = "VNDG2PFPMNB4FKMC4ORASZ2JJ4",
                                utbetalinger =
                                    listOf(
                                        ApiGrunnlagUtbetaling(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31"), 100.0),
                                        ApiGrunnlagUtbetaling(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-15"), 50.0),
                                    ),
                                vedtattTidspunkt = LocalDateTime.parse("2024-03-01T12:34:56.789"),
                            ),
                        )
                }
            }
        }
        code(HttpStatusCode.BadRequest) {
            description =
                "Ugyldig request. `fodselsnummer` mangler eller er `null`, body er ikke et JSON-objekt, " +
                "eller `fom` er ikke en gyldig dato. Svaret er ren tekst."
            body<String> {
                mediaTypes = setOf(ContentType.Text.Plain)
                example("manglerFødselsnummer") { value = UgyldigGrunnlagRequest.MANGLER_FØDSELSNUMMER.feilmelding }
                example("ugyldigFom") { value = UgyldigGrunnlagRequest.UGYLDIG_FOM.feilmelding }
            }
        }
        code(HttpStatusCode.InternalServerError) {
            description =
                "Oppslaget feilet, eller en uventet feil. Når oppslaget feiler, er svaret ren tekst. " +
                "Når body ikke kan leses, eller noe annet uventet går galt, er svaret `application/problem+json`."
            body<String> {
                mediaTypes = setOf(ContentType.Text.Plain)
                example("feilVedOppslag") { value = FEIL_VED_OPPSLAG }
            }
        }
    }
}
