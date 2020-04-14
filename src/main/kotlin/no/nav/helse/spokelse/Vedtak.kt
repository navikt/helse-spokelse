package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class Vedtak(
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val utbetalinger: List<Utbetaling>,
    val opprettet: LocalDateTime
)

class Utbetaling(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)

 class FpVedtak(
     val vedtaksreferanse: UUID,
     val utbetalinger: List<Utbetalingsperiode>,
     val vedtattTidspunkt: LocalDateTime
 )

class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)
