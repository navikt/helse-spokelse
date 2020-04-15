package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.LocalDateTime

class Vedtak(
    val fødselsnummer: String,
    val førsteFraværsdag: LocalDate,
    val utbetalinger: List<Utbetaling>,
    val opprettet: LocalDateTime
)

class Utbetaling(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)

class FpVedtak(
    val vedtaksreferanse: LocalDate,
    val utbetalinger: List<Utbetalingsperiode>,
    val vedtattTidspunkt: LocalDateTime
)

class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)
