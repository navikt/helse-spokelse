package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class OldVedtak(
    val vedtaksperiodeId: UUID,
    val fødselsnummer: String,
    val orgnummer: String,
    val utbetalinger: List<OldUtbetaling>,
    val opprettet: LocalDateTime,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int?,
    val dokumenter: Dokumenter
)

class OldUtbetaling(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double,
    val dagsats: Int,
    val beløp: Int,
    val totalbeløp: Int
)

data class Vedtak(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val orgnummer: String,
    val dokumenter: Dokumenter,
    val oppdrag: List<Oppdrag>,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val opprettet: LocalDateTime
) {
    data class Oppdrag(
        val mottaker: String,
        val fagområde: String,
        val fagsystemId: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<Utbetalingslinje>
    ) {
        data class Utbetalingslinje(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int,
            val beløp: Int,
            val grad: Double,
            val sykedager: Int
        )
    }
}

class FpVedtak(
    val vedtaksreferanse: String,
    val utbetalinger: List<Utbetalingsperiode>,
    val vedtattTidspunkt: LocalDateTime
)

class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)
