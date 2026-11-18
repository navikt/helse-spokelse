@file:UseContextualSerialization(LocalDate::class, LocalDateTime::class)

package no.nav.helse.spokelse.grunnlag

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.github.smiley4.schemakenerator.core.annotations.Format
import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.github.smiley4.schemakenerator.core.annotations.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
internal data class ApiGrunnlagRequest(
    @Description("Fødselsnummeret eller D-nummeret til personen. Formatet valideres ikke.")
    @Example("12345678911")
    @Required
    val fodselsnummer: String? = null,
    @Description("Gir bare vedtak med utbetalinger fra og med denne datoen. Uten `fom` får du alle vedtakene.")
    @Example("2024-01-01")
    @Format("date")
    @Optional
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

@Serializable
@Description("Et vedtak i Spleis med utbetalinger. Hvert oppdrag er et eget vedtak, så utbetalinger til arbeidsgiver og til personen kommer hver for seg.")
internal data class ApiGrunnlagVedtak(
    @Description("Fagsystem-id-en til oppdraget.")
    @Example("VNDG2PFPMNB4FKMC4ORASZ2JJ4")
    val vedtaksreferanse: String,
    @Description("Utbetalingsperiodene i vedtaket.")
    val utbetalinger: List<ApiGrunnlagUtbetaling>,
    @Description("Når vedtaket ble utbetalt. Tidspunktet har ikke tidssone, for eksempel `2024-03-01T12:34:56.789`.")
    @Example("2024-03-01T12:34:56.789")
    val vedtattTidspunkt: LocalDateTime,
)

@Serializable
internal data class ApiGrunnlagUtbetaling(
    @Description("Første dag i utbetalingsperioden.")
    @Example("2024-01-01")
    val fom: LocalDate,
    @Description("Siste dag i utbetalingsperioden.")
    @Example("2024-01-31")
    val tom: LocalDate,
    @Description("Sykmeldingsgraden i prosent, som desimaltall, for eksempel `100.0`.")
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
