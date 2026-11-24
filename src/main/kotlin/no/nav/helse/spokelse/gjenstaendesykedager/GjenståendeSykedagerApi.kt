@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.spokelse.gjenstaendesykedager

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.github.smiley4.schemakenerator.core.annotations.Min
import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.github.smiley4.schemakenerator.core.annotations.Pattern
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import no.nav.helse.spokelse.Tags
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.postMedRolle
import no.nav.helse.spokelse.problemJson
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

private val logger = navngittLogger("no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerApi")

private val objectMapper =
    jacksonMapperBuilder()
        .withCoercionConfig(LogicalType.Textual) { config ->
            config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
        }.build()

@Serializable
internal data class ApiGjenståendeSykedagerRequest(
    @Description("Fødselsnummeret eller D-nummeret til personen.")
    @Pattern("^\\d{11}$")
    @Example("12345678911")
    val fødselsnummer: String,
)

@Serializable
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class ApiGjenståendeSykedager(
    @Description("Hva svaret bygger på. Se beskrivelsen av endepunktet. Nye verdier kan komme. Konsumenten må tåle dem.")
    val grunnlag: ApiGjenståendeSykedagerGrunnlag,
    @Description(
        "Antall sykedager personen hadde igjen per `vurdertTilOgMed`. Bare med når `grunnlag` er `VEDTAK_I_NY_LØSNING`, " +
            "`SISTE_UTBETALING_I_INFOTRYGD` eller `MAKSDATO_I_INFOTRYGD`. Mangler ellers. Er aldri `null`.",
    )
    @Min(0)
    @Optional
    val gjenståendeDager: Int? = null,
    @Description("Dagen tallet gjelder for. Bare med sammen med `gjenståendeDager`. Mangler ellers.")
    @Optional
    val vurdertTilOgMed: LocalDate? = null,
)

@Serializable
internal enum class ApiGjenståendeSykedagerGrunnlag {
    VEDTAK_I_NY_LØSNING,
    SISTE_UTBETALING_I_INFOTRYGD,
    MAKSDATO_I_INFOTRYGD,
    MANGLER_MAKSDATO_I_INFOTRYGD,
    INGEN_SYKEPENGER_SISTE_26_UKER,
}

@Serializable
internal data class ApiGjenståendeSykedagerFeil(
    val feilkode: ApiGjenståendeSykedagerFeilkode,
    @Description("Forklaring for mennesker. Ikke bruk den til å styre logikk.")
    val melding: String,
)

@Serializable
internal enum class ApiGjenståendeSykedagerFeilkode {
    UGYLDIG_FORESPØRSEL,
}

internal fun Route.gjenståendeSykedagerApi(
    gjenståendeSykedager: GjenståendeSykedager,
    meterRegistry: MeterRegistry,
) {
    fun tellUtfall(grunnlag: Grunnlag?) = meterRegistry.counter("gjenstaende_sykedager_oppslag", "utfall", grunnlag?.name ?: "FEIL").increment()

    postMedRolle("/gjenstaende-sykedager", "aap-les", "personoversikt-les", dokumentasjon = { gjenståendeSykedagerDokumentasjon() }) {
        val personidentifikator =
            personidentifikatorFraRequest()
                ?: return@postMedRolle call.respond(HttpStatusCode.BadRequest, ugyldigForespørsel)

        val resultat =
            try {
                gjenståendeSykedager.hent(personidentifikator)
            } catch (exception: Exception) {
                tellUtfall(null)
                throw exception
            }
        val response = resultat.tilApi()

        logger.info(
            "Svarer på /gjenstaende-sykedager",
            "fødselsnummer" to personidentifikator.toString(),
            "grunnlag" to response.grunnlag.name,
            "gjenståendeDager" to response.gjenståendeDager?.toString(),
            "vurdertTilOgMed" to response.vurdertTilOgMed?.toString(),
        )
        tellUtfall(resultat.grunnlag)
        call.respond(HttpStatusCode.OK, response)
    }
}

private val ugyldigForespørsel = ApiGjenståendeSykedagerFeil(ApiGjenståendeSykedagerFeilkode.UGYLDIG_FORESPØRSEL, "Forventer en body med fødselsnummer på 11 siffer")

private fun GjenståendeSykedager.Resultat.tilApi() =
    ApiGjenståendeSykedager(
        grunnlag =
            when (grunnlag) {
                Grunnlag.VEDTAK_I_NY_LØSNING -> ApiGjenståendeSykedagerGrunnlag.VEDTAK_I_NY_LØSNING
                Grunnlag.SISTE_UTBETALING_I_INFOTRYGD -> ApiGjenståendeSykedagerGrunnlag.SISTE_UTBETALING_I_INFOTRYGD
                Grunnlag.MAKSDATO_I_INFOTRYGD -> ApiGjenståendeSykedagerGrunnlag.MAKSDATO_I_INFOTRYGD
                Grunnlag.MANGLER_MAKSDATO_I_INFOTRYGD -> ApiGjenståendeSykedagerGrunnlag.MANGLER_MAKSDATO_I_INFOTRYGD
                Grunnlag.INGEN_SYKEPENGER_SISTE_26_UKER -> ApiGjenståendeSykedagerGrunnlag.INGEN_SYKEPENGER_SISTE_26_UKER
            },
        gjenståendeDager = gjenståendeDager,
        vurdertTilOgMed = vurdertTilOgMed,
    )

private suspend fun RoutingContext.personidentifikatorFraRequest(): Personidentifikator? {
    val request = runCatching { objectMapper.readValue<ApiGjenståendeSykedagerRequest>(call.receiveText()) }.getOrNull() ?: return null
    return runCatching { Personidentifikator(request.fødselsnummer) }.getOrNull()
}

private fun RouteConfig.gjenståendeSykedagerDokumentasjon() {
    tags(Tags.GJENSTÅENDE_SYKEDAGER)
    operationId = "hentGjenstaendeSykedager"
    summary = "Hent gjenstående sykedager"
    description = BESKRIVELSE
    request {
        body<ApiGjenståendeSykedagerRequest> {
            required = true
            example("fødselsnummer") { value = ApiGjenståendeSykedagerRequest("12345678911") }
        }
    }
    response {
        code(HttpStatusCode.OK) {
            description = "Oppslaget gikk bra. `grunnlag` sier hva svaret bygger på."
            body<ApiGjenståendeSykedager> {
                example("vedtakINyLøsning") {
                    summary = "Siste vedtak er i ny løsning"
                    value = ApiGjenståendeSykedager(ApiGjenståendeSykedagerGrunnlag.VEDTAK_I_NY_LØSNING, 112, LocalDate.parse("2026-09-30"))
                }
                example("sisteUtbetalingIInfotrygd") {
                    summary = "Infotrygd har utbetalt etter siste vedtak"
                    value = ApiGjenståendeSykedager(ApiGjenståendeSykedagerGrunnlag.SISTE_UTBETALING_I_INFOTRYGD, 87, LocalDate.parse("2026-10-30"))
                }
                example("maksdatoIInfotrygd") {
                    summary = "Siste sykefravær er bare i Infotrygd, og Infotrygd har maksdato"
                    value = ApiGjenståendeSykedager(ApiGjenståendeSykedagerGrunnlag.MAKSDATO_I_INFOTRYGD, 10, LocalDate.parse("2026-10-30"))
                }
                example("manglerMaksdatoIInfotrygd") {
                    summary = "Siste sykefravær er bare i Infotrygd, men Infotrygd har ingen maksdato"
                    value = ApiGjenståendeSykedager(ApiGjenståendeSykedagerGrunnlag.MANGLER_MAKSDATO_I_INFOTRYGD)
                }
                example("ingenSykepengerSiste26Uker") {
                    summary = "Ingen vedtak eller utbetalinger de siste 26 ukene"
                    value = ApiGjenståendeSykedager(ApiGjenståendeSykedagerGrunnlag.INGEN_SYKEPENGER_SISTE_26_UKER)
                }
            }
        }
        code(HttpStatusCode.BadRequest) {
            description = "Ugyldig request. Body mangler, er ikke JSON, eller `fødselsnummer` er ikke en streng på 11 siffer."
            body<ApiGjenståendeSykedagerFeil> {
                example("ugyldigForespørsel") { value = ugyldigForespørsel }
            }
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Uventet feil, for eksempel at oppslaget mot Infotrygd eller beregningen i spleis-api feilet. Prøv igjen senere."
            problemJson()
        }
    }
}

private val BESKRIVELSE =
    """
    Gir antall gjenstående sykedager for en person, og hva tallet bygger på.

    Konsumenter: AAP (`behandlingsflyt` og `postmottak-backend`) og Personoversikt (`modiapersonoversikt-api`).

    Alle utfall av oppslaget gir `200` med feltet `grunnlag`:
    - `VEDTAK_I_NY_LØSNING`: Tallet er beregnet da siste vedtak ble fattet. `vurdertTilOgMed` er siste dag i vedtaket.
    - `SISTE_UTBETALING_I_INFOTRYGD`: Infotrygd har en utbetaling etter siste vedtak. Tallet beregnes ved oppslaget, med dager
      fra begge løsninger og de samme reglene som saksbehandlingen i ny løsning. `vurdertTilOgMed` er siste dag i siste utbetaling
      i Infotrygd. Alle hverdager i en utbetalingsperiode i Infotrygd teller som utbetalt, så tallet kan bli for lavt.
    - `MAKSDATO_I_INFOTRYGD`: Personen har ingen vedtak i ny løsning, bare utbetalinger i Infotrygd. Tallet er ukedagene
      (mandag–fredag, helligdager telles med) fra dagen etter `vurdertTilOgMed` til og med Infotrygds maksdato.
      `vurdertTilOgMed` er siste dag i siste utbetaling i Infotrygd.
    - `MANGLER_MAKSDATO_I_INFOTRYGD`: Som `MAKSDATO_I_INFOTRYGD`, men Infotrygd har ingen maksdato. Ingen tall.
    - `INGEN_SYKEPENGER_SISTE_26_UKER`: Vi finner ingen vedtak eller utbetalinger de siste 26 ukene. Personen får full rett
      ved ny sykdom. Ingen tall. Sykdom som ikke er behandlet ennå, er ikke med, så personen kan ha brukt dager vi ikke ser.
      Vedtak fra før 16. mars 2022 og annullerte vedtak er heller ikke med.

    Nye verdier i `grunnlag` kan komme. Konsumenten må tåle dem.

    **Tallet gjelder per `vurdertTilOgMed`, ikke per i dag.** `gjenståendeDager` er antall sykedager personen hadde igjen
    per `vurdertTilOgMed`. Det eneste vi sjekker mot dagen i dag, er om personen har fått ny rett etter 26 uker uten sykepenger.

    For `VEDTAK_I_NY_LØSNING` tar tallet hensyn til dette fram til og med `vurdertTilOgMed`:
    - sykedager hos alle arbeidsgivere og sykedager utbetalt i Infotrygd
    - ny rett etter 26 uker uten sykepenger
    - at sykedager eldre enn tre år faller bort
    - at personen har rett til høyst 60 sykedager etter fylte 67 år, og ingen rett etter fylte 70 år
    - at graderte dager teller som hele dager, slik loven sier

    Det som skjer etter `vurdertTilOgMed`, er ikke med:
    - sykdom som ikke er behandlet ennå
    - sykedager som blir eldre enn tre år

    Personen kan derfor ha både flere og færre dager igjen i dag enn tallet viser.

    Siste vedtak er vedtaket med senest `vurdertTilOgMed`. Det kan være et vedtak med eller uten utbetaling.
    Et annullert vedtak regnes ikke med, og da gjelder forrige vedtak.
    Siste utbetaling i Infotrygd er utbetalingsperioden med senest `tom`. Ferie og andre periodetyper er ikke utbetalinger.

    **Kjent begrensning:** Hvis et vedtak uten utbetaling er annullert, fanges ikke annulleringen opp,
    og svaret kan fortsatt bygge på det annullerte vedtaket.
    """.trimIndent()
