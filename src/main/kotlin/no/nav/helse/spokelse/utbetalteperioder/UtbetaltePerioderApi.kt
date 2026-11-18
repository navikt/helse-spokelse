package no.nav.helse.spokelse.utbetalteperioder

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import no.nav.helse.spokelse.Tags
import no.nav.helse.spokelse.postMedRolle
import no.nav.helse.spokelse.problemJson
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.util.Optional

private val objectMapper = jacksonObjectMapper()
private val logger = navngittLogger("no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioderApi")

internal fun Route.utbetaltePerioderApi(utbetaltePerioder: UtbetaltePerioder) {
    postMedRolle("/utbetalte-perioder", "spleiselaget-les", dokumentasjon = { utbetaltePerioderDokumentasjon() }) {
        svar<ApiUtbetaltePerioderMedOppløsningRequest, _> { request ->
            utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, request.tilGroupBy()).tilApiUtbetaltePerioder(request.tilGroupBy())
        }
    }
    postMedRolle("/utbetalte-perioder-aap", "aap-les", dokumentasjon = { utbetaltePerioderMedGradDokumentasjon("Aap", AAP) }) {
        svar<ApiUtbetaltePerioderRequest, _> { request ->
            utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, setOf(GroupBy.grad)).tilApiUtbetaltePerioderMedGrad()
        }
    }
    postMedRolle("/utbetalte-perioder-dagpenger", "dagpenger-les", dokumentasjon = { utbetaltePerioderMedGradDokumentasjon("Dagpenger", DAGPENGER) }) {
        svar<ApiUtbetaltePerioderRequest, _> { request ->
            utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, setOf(GroupBy.grad)).tilApiUtbetaltePerioderMedGrad()
        }
    }
    postMedRolle("/utbetalte-perioder-personoversikt", "personoversikt-les", dokumentasjon = { utbetaltePerioderPersonoversiktDokumentasjon() }) {
        svar<ApiUtbetaltePerioderRequest, _> { request ->
            utbetaltePerioder
                .hent(request.tilPersonidentifikatorer(), request.fom, request.tom, setOf(GroupBy.grad, GroupBy.organisasjonsnummer, GroupBy.kilde))
                .tilApiUtbetaltePerioderPersonoversikt()
        }
    }
}

private suspend inline fun <reified Request : Any, reified Response : Any> RoutingContext.svar(lagSvar: (Request) -> Response) {
    val requestTekst = call.receiveText()
    val response = lagSvar(objectMapper.readValue<Request>(requestTekst))
    logger.info("/utbetalte-perioder: request og response", "request" to requestTekst, "response" to objectMapper.writeValueAsString(response))
    call.respond(HttpStatusCode.OK, response)
}

private const val AAP = "AAP (`behandlingsflyt` og `postmottak-backend`)"
private const val DAGPENGER = "Dagpenger (`dp-oppslag-ytelser`)"

private val januar = LocalDate.parse("2024-01-01")
private val tiJanuar = LocalDate.parse("2024-01-10")
private val ellevteJanuar = LocalDate.parse("2024-01-11")
private val tjueJanuar = LocalDate.parse("2024-01-20")
private val sisteJanuar = LocalDate.parse("2024-01-31")
private val eksempelRequest = ApiUtbetaltePerioderRequest(listOf("12345678911"), januar, sisteJanuar)

private const val UGYLDIG_REQUEST =
    "Ugyldig request eller uventet feil. Spøkelse svarer `500`, ikke `400`, også når requesten er ugyldig: body er ikke JSON, " +
        "et påkrevd felt mangler, `personidentifikatorer` er tom eller har en verdi som ikke er 11 siffer, eller `tom` er før `fom`."

private fun RouteConfig.utbetaltePerioderFelles(
    operasjon: String,
    oppsummering: String,
) {
    tags(Tags.UTBETALTE_PERIODER)
    operationId = operasjon
    summary = oppsummering
}

private fun RouteConfig.utbetaltePerioderMedGradDokumentasjon(
    konsument: String,
    konsumenter: String,
) {
    utbetaltePerioderFelles("hentUtbetaltePerioder$konsument", "Hent utbetalte perioder med grad")
    description =
        """
        Gir de utbetalte sykepengeperiodene til en person mellom `fom` og `tom`, fra Spleis og Infotrygd.

        Konsumenter: $konsumenter.

        Periodene er gruppert på grad: sammenhengende og overlappende perioder med samme grad slås sammen til én periode.
        Hver periode har bare `grad`, `fom` og `tom`. Et eventuelt `oppløsning` i requesten ignoreres.
        """.trimIndent()
    request {
        body<ApiUtbetaltePerioderRequest> {
            required = true
            example("enPerson") { value = eksempelRequest }
        }
    }
    response {
        code(HttpStatusCode.OK) {
            description = "Oppslaget gikk bra. Listen er tom når personen ikke har utbetalte perioder mellom `fom` og `tom`."
            body<ApiUtbetaltePerioderMedGrad> {
                example("toPerioder") {
                    value =
                        ApiUtbetaltePerioderMedGrad(
                            listOf(
                                ApiUtbetaltPeriodeMedGrad(100, januar, tiJanuar),
                                ApiUtbetaltPeriodeMedGrad(50, ellevteJanuar, tjueJanuar),
                            ),
                        )
                }
            }
        }
        code(HttpStatusCode.InternalServerError) {
            description = UGYLDIG_REQUEST
            problemJson()
        }
    }
}

private fun RouteConfig.utbetaltePerioderPersonoversiktDokumentasjon() {
    utbetaltePerioderFelles("hentUtbetaltePerioderPersonoversikt", "Hent utbetalte perioder for Personoversikt")
    description =
        """
        Gir de utbetalte sykepengeperiodene til en person mellom `fom` og `tom`, fra Spleis og Infotrygd.

        Konsument: Personoversikt (`modiapersonoversikt-api`).

        Periodene er gruppert på grad, organisasjonsnummer og kilde: sammenhengende og overlappende perioder med samme grad
        hos samme arbeidsgiver slås sammen til én periode. Perioder fra Infotrygd og Spleis grupperes hver for seg.
        Perioder uten arbeidsgiver grupperes ikke, og har `organisasjonsnummer: null`.
        Et eventuelt `oppløsning` i requesten ignoreres.
        """.trimIndent()
    request {
        body<ApiUtbetaltePerioderRequest> {
            required = true
            example("enPerson") { value = eksempelRequest }
        }
    }
    response {
        code(HttpStatusCode.OK) {
            description = "Oppslaget gikk bra. Listen er tom når personen ikke har utbetalte perioder mellom `fom` og `tom`."
            body<ApiUtbetaltePerioderPersonoversikt> {
                example("medOgUtenArbeidsgiver") {
                    value =
                        ApiUtbetaltePerioderPersonoversikt(
                            listOf(
                                ApiUtbetaltPeriodePersonoversikt("999999999", 100, januar, tiJanuar, setOf("Spleis", "TbdUtbetaling")),
                                ApiUtbetaltPeriodePersonoversikt(null, 100, ellevteJanuar, tjueJanuar, setOf("Infotrygd")),
                            ),
                        )
                }
            }
        }
        code(HttpStatusCode.InternalServerError) {
            description = UGYLDIG_REQUEST
            problemJson()
        }
    }
}

private fun RouteConfig.utbetaltePerioderDokumentasjon() {
    utbetaltePerioderFelles("hentUtbetaltePerioder", "Hent utbetalte perioder gruppert etter oppløsning")
    description =
        """
        Gir de utbetalte sykepengeperiodene til en person mellom `fom` og `tom`, fra Spleis og Infotrygd.

        Konsument: spapi.

        `oppløsning` i requesten bestemmer hva periodene grupperes på, og dermed hvilke felter hver periode har.
        Sammenhengende og overlappende perioder med like verdier for det som er valgt, slås sammen til én periode.
        `fom`, `tom` og `tags` er alltid med. Gyldige verdier i `oppløsning`:
        - `personidentifikator`: gir feltet `personidentifikator`.
        - `organisasjonsnummer`: gir feltet `organisasjonsnummer`. Det er `null` når perioden ikke har arbeidsgiver.
        - `grad`: gir feltet `grad`.
        - `kilde`: legger ikke til noe felt, men gjør at perioder fra Infotrygd og Spleis grupperes hver for seg.

        `oppløsning` kan være tom. Da får du bare `fom`, `tom` og `tags`. Perioder uten arbeidsgiver grupperes aldri.
        """.trimIndent()
    request {
        body<ApiUtbetaltePerioderMedOppløsningRequest> {
            required = true
            example("gruppertPåArbeidsgiverOgGrad") {
                value =
                    ApiUtbetaltePerioderMedOppløsningRequest(
                        listOf("12345678911"),
                        januar,
                        sisteJanuar,
                        listOf(ApiOppløsning.organisasjonsnummer, ApiOppløsning.grad),
                    )
            }
        }
    }
    response {
        code(HttpStatusCode.OK) {
            description = "Oppslaget gikk bra. Listen er tom når personen ikke har utbetalte perioder mellom `fom` og `tom`."
            body<ApiUtbetaltePerioder> {
                example("gruppertPåArbeidsgiverOgGrad") {
                    summary = "`oppløsning` er `organisasjonsnummer` og `grad`"
                    value =
                        ApiUtbetaltePerioder(
                            listOf(
                                ApiUtbetaltPeriode(
                                    organisasjonsnummer = Optional.of("999999999"),
                                    grad = 100,
                                    fom = januar,
                                    tom = tiJanuar,
                                    tags = setOf("Spleis", "TbdUtbetaling"),
                                ),
                                ApiUtbetaltPeriode(
                                    organisasjonsnummer = Optional.empty(),
                                    grad = 100,
                                    fom = ellevteJanuar,
                                    tom = tjueJanuar,
                                    tags = setOf("Infotrygd"),
                                ),
                            ),
                        )
                }
                example("utenOppløsning") {
                    summary = "`oppløsning` er tom"
                    value =
                        ApiUtbetaltePerioder(
                            listOf(ApiUtbetaltPeriode(fom = januar, tom = tjueJanuar, tags = setOf("Spleis", "TbdUtbetaling", "Infotrygd"))),
                        )
                }
            }
        }
        code(HttpStatusCode.InternalServerError) {
            description = "$UGYLDIG_REQUEST Det gjelder også når `oppløsning` mangler eller har en ukjent verdi."
            problemJson()
        }
    }
}
