@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.github.smiley4.schemakenerator.core.annotations.Pattern
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import no.nav.helse.spokelse.OptionalStringSerializer
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import java.time.LocalDate

private const val PERSONIDENTIFIKATORER = "Fødselsnummerne eller D-nummerne til personen, hvert på 11 siffer. Minst én."
private const val FOM = "Første dag i perioden det skal hentes utbetalinger for."
private const val TOM = "Siste dag i perioden det skal hentes utbetalinger for. Kan ikke være før `fom`."
private const val PERIODE_FOM = "Første dag i den utbetalte perioden."
private const val PERIODE_TOM = "Siste dag i den utbetalte perioden."
private const val GRAD = "Sykmeldingsgraden i prosent."
private const val TAGS =
    "Hvor perioden kommer fra. Kjente verdier er `Infotrygd`, `Spleis`, `TbdUtbetaling`, `VedtakOppdragOgUtbetaling`, " +
        "`OldVedtakOgOldUtbetaling` og `GamleUtbetalinger`. Tags fra Infotrygd sendes videre, så nye verdier kan komme. Konsumenten må tåle dem."

@Serializable
internal data class ApiUtbetaltePerioderRequest(
    @Description(PERSONIDENTIFIKATORER)
    val personidentifikatorer: List<String>,
    @Description(FOM)
    val fom: LocalDate,
    @Description(TOM)
    val tom: LocalDate,
)

@Serializable
internal data class ApiUtbetaltePerioderMedOppløsningRequest(
    @Description(PERSONIDENTIFIKATORER)
    val personidentifikatorer: List<String>,
    @Description(FOM)
    val fom: LocalDate,
    @Description(TOM)
    val tom: LocalDate,
    @Description(
        "Hva periodene skal grupperes på, og dermed hvilke felter hver periode har. Kan være tom. " +
            "`personidentifikator`, `organisasjonsnummer` og `grad` gir feltet med samme navn. " +
            "`kilde` legger ikke til noe felt, men gjør at perioder fra Infotrygd og Spleis grupperes hver for seg.",
    )
    val oppløsning: List<ApiOppløsning>,
)

@Serializable
internal enum class ApiOppløsning {
    organisasjonsnummer,
    personidentifikator,
    grad,
    kilde,
}

@Serializable
internal data class ApiUtbetaltePerioderMedGrad(
    @Description("De utbetalte periodene, gruppert på grad. Tom når ingen perioder finnes.")
    val utbetaltePerioder: List<ApiUtbetaltPeriodeMedGrad>,
)

@Serializable
internal data class ApiUtbetaltPeriodeMedGrad(
    @Description(GRAD)
    val grad: Int,
    @Description(PERIODE_FOM)
    val fom: LocalDate,
    @Description(PERIODE_TOM)
    val tom: LocalDate,
)

@Serializable
internal data class ApiUtbetaltePerioderPersonoversikt(
    @Description(
        "De utbetalte periodene, gruppert på grad, organisasjonsnummer og kilde. " +
            "Perioder fra Infotrygd og Spleis grupperes hver for seg. Tom når ingen perioder finnes.",
    )
    val utbetaltePerioder: List<ApiUtbetaltPeriodePersonoversikt>,
)

@Serializable
internal data class ApiUtbetaltPeriodePersonoversikt(
    @Description("Organisasjonsnummeret til arbeidsgiveren. Alltid med. `null` når perioden ikke har arbeidsgiver.")
    @Example("999999999")
    val organisasjonsnummer: String?,
    @Description(GRAD)
    val grad: Int,
    @Description(PERIODE_FOM)
    val fom: LocalDate,
    @Description(PERIODE_TOM)
    val tom: LocalDate,
    @Description(TAGS)
    val tags: Set<String>,
)

@Serializable
internal data class ApiUtbetaltePerioder(
    @Description("De utbetalte periodene, gruppert etter `oppløsning` i requesten. Tom når ingen perioder finnes.")
    val utbetaltePerioder: List<ApiUtbetaltPeriode>,
)

@Serializable
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class ApiUtbetaltPeriode(
    @Description("Fødselsnummeret eller D-nummeret til personen. Bare med når `oppløsning` har `personidentifikator`.")
    @Pattern("^\\d{11}$")
    @Example("12345678911")
    @Optional
    val personidentifikator: String? = null,
    @Description(
        "Organisasjonsnummeret til arbeidsgiveren. Bare med når `oppløsning` har `organisasjonsnummer`. " +
            "`null` når perioden ikke har arbeidsgiver.",
    )
    @Example("999999999")
    @Optional
    @Serializable(with = OptionalStringSerializer::class)
    val organisasjonsnummer: java.util.Optional<String>? = null,
    @Description("$GRAD Bare med når `oppløsning` har `grad`.")
    @Optional
    val grad: Int? = null,
    @Description(PERIODE_FOM)
    val fom: LocalDate,
    @Description(PERIODE_TOM)
    val tom: LocalDate,
    @Description(TAGS)
    val tags: Set<String>,
)

internal fun ApiUtbetaltePerioderRequest.tilPersonidentifikatorer() = personidentifikatorer.map(::Personidentifikator).toSet()

internal fun ApiUtbetaltePerioderMedOppløsningRequest.tilPersonidentifikatorer() = personidentifikatorer.map(::Personidentifikator).toSet()

internal fun ApiUtbetaltePerioderMedOppløsningRequest.tilGroupBy() = oppløsning.map { it.tilDomene() }.toSet()

private fun ApiOppløsning.tilDomene() =
    when (this) {
        ApiOppløsning.organisasjonsnummer -> GroupBy.organisasjonsnummer
        ApiOppløsning.personidentifikator -> GroupBy.personidentifikator
        ApiOppløsning.grad -> GroupBy.grad
        ApiOppløsning.kilde -> GroupBy.kilde
    }

internal fun List<SpøkelsePeriode>.tilApiUtbetaltePerioderMedGrad() =
    ApiUtbetaltePerioderMedGrad(
        map { ApiUtbetaltPeriodeMedGrad(grad = it.grad, fom = it.fom, tom = it.tom) },
    )

internal fun List<SpøkelsePeriode>.tilApiUtbetaltePerioderPersonoversikt() =
    ApiUtbetaltePerioderPersonoversikt(
        map {
            ApiUtbetaltPeriodePersonoversikt(
                organisasjonsnummer = it.organisasjonsnummer,
                grad = it.grad,
                fom = it.fom,
                tom = it.tom,
                tags = it.tags,
            )
        },
    )

internal fun List<SpøkelsePeriode>.tilApiUtbetaltePerioder(groupBy: Set<GroupBy>) =
    ApiUtbetaltePerioder(
        map {
            ApiUtbetaltPeriode(
                personidentifikator = it.personidentifikator.toString().takeIf { GroupBy.personidentifikator in groupBy },
                organisasjonsnummer =
                    java.util.Optional
                        .ofNullable(it.organisasjonsnummer)
                        .takeIf { GroupBy.organisasjonsnummer in groupBy },
                grad = it.grad.takeIf { GroupBy.grad in groupBy },
                fom = it.fom,
                tom = it.tom,
                tags = it.tags,
            )
        },
    )
