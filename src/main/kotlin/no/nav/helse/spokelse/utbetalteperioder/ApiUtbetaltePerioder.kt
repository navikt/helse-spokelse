package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import java.time.LocalDate

internal data class ApiUtbetaltePerioderRequest(
    val personidentifikatorer: List<String>,
    val fom: LocalDate,
    val tom: LocalDate,
)

internal data class ApiUtbetaltePerioderMedOppløsningRequest(
    val personidentifikatorer: List<String>,
    val fom: LocalDate,
    val tom: LocalDate,
    val oppløsning: List<ApiOppløsning>,
)

internal enum class ApiOppløsning {
    organisasjonsnummer,
    personidentifikator,
    grad,
    kilde,
}

internal data class ApiUtbetaltePerioderMedGrad(
    val utbetaltePerioder: List<ApiUtbetaltPeriodeMedGrad>,
)

internal data class ApiUtbetaltPeriodeMedGrad(
    val grad: Int,
    val fom: LocalDate,
    val tom: LocalDate,
)

internal data class ApiUtbetaltePerioderPersonoversikt(
    val utbetaltePerioder: List<ApiUtbetaltPeriodePersonoversikt>,
)

internal data class ApiUtbetaltPeriodePersonoversikt(
    val organisasjonsnummer: String?,
    val grad: Int,
    val fom: LocalDate,
    val tom: LocalDate,
    val tags: Set<String>,
)

internal data class ApiUtbetaltePerioder(
    val utbetaltePerioder: List<ApiUtbetaltPeriode>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class ApiUtbetaltPeriode(
    val personidentifikator: String? = null,
    val organisasjonsnummer: java.util.Optional<String>? = null,
    val grad: Int? = null,
    val fom: LocalDate,
    val tom: LocalDate,
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
