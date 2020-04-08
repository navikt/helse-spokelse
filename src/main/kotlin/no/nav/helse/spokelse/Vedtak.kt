package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class Vedtak(
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)

data class FpData(
    val aktør: String,
    val vedtattTidspunkt: LocalDateTime,
    val saksnummer: String,
    val vedtakReferanse: String,
    val periode: Pair<LocalDate, LocalDate>,
    val anvist: List<Anvisning>
)

data class Anvisning(
    val periode: Pair<LocalDate, LocalDate>,
    val beløp: Double,
    val dagsats: Double,
    val utbetalingsgrad: Double
)
