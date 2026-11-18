package no.nav.helse.spokelse.grunnlag

import java.time.LocalDate
import java.time.LocalDateTime

internal data class ApiGrunnlagRequest(
    val fodselsnummer: String? = null,
    val fom: String? = null,
) {
    fun valider(): GrunnlagRequestValidering {
        if (fodselsnummer == null) return UgyldigGrunnlagRequest.MANGLER_FØDSELSNUMMER
        if (fom == null) return GyldigGrunnlagRequest(fodselsnummer, null)
        return runCatching { LocalDate.parse(fom) }
            .map { GyldigGrunnlagRequest(fodselsnummer, it) }
            .getOrElse { UgyldigGrunnlagRequest.UGYLDIG_FOM }
    }
}

internal sealed interface GrunnlagRequestValidering

internal data class GyldigGrunnlagRequest(
    val fødselsnummer: String,
    val fom: LocalDate?,
) : GrunnlagRequestValidering

internal enum class UgyldigGrunnlagRequest(
    val feilmelding: String,
) : GrunnlagRequestValidering {
    MANGLER_FØDSELSNUMMER("Mangler fodselsnummer request body"),
    UGYLDIG_FOM("Ugyldig fom request body"),
}

internal data class ApiGrunnlagVedtak(
    val vedtaksreferanse: String,
    val utbetalinger: List<ApiGrunnlagUtbetaling>,
    val vedtattTidspunkt: LocalDateTime,
)

internal data class ApiGrunnlagUtbetaling(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double,
)

internal fun List<FpVedtak>.tilApiGrunnlagVedtak() =
    map { vedtak ->
        ApiGrunnlagVedtak(
            vedtaksreferanse = vedtak.vedtaksreferanse,
            utbetalinger = vedtak.utbetalinger.map { ApiGrunnlagUtbetaling(fom = it.fom, tom = it.tom, grad = it.grad) },
            vedtattTidspunkt = vedtak.vedtattTidspunkt,
        )
    }
