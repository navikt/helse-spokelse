package no.nav.helse.spokelse.tbdutbetaling

import java.time.LocalDate

internal data class Utbetalingslinje(
    internal val fom: LocalDate,
    internal val tom: LocalDate,
    internal val grad: Double
)
