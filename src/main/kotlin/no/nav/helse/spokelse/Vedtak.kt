package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class Vedtak(
    val fødselsnummer: String,
    val gruppeId: UUID,
    val vedtaksperiodeId: UUID,
    val utbetalinger: List<Utbetaling>,
    val opprettet: LocalDateTime
)

class Utbetaling(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)

data class FpVedtak(
    val aktør: String,
    val vedtattTidspunkt: LocalDateTime,
    val saksnummer: String,
    val vedtakReferanse: String,
    val perioder: List<Utbetalingsperioder>
)

data class Utbetalingsperioder(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalingsgrad: Double
)
