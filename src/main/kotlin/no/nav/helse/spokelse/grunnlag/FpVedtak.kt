package no.nav.helse.spokelse.grunnlag

import java.time.LocalDate
import java.time.LocalDateTime

data class FpVedtak(
    val vedtaksreferanse: String,
    val utbetalinger: List<Utbetalingsperiode>,
    val vedtattTidspunkt: LocalDateTime
)

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)
