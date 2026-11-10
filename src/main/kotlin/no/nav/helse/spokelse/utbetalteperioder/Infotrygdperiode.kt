package no.nav.helse.spokelse.utbetalteperioder

import java.time.LocalDate

internal data class Infotrygdperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val type: String,
    val maksdato: LocalDate?,
) {
    internal val erUtbetaling get() = type in UTBETALINGSTYPER

    private companion object {
        // De samme typene som sykepengeperioder-api gir når inkluderAllePeriodetyper er false.
        private val UTBETALINGSTYPER = setOf("UTBETALING", "REDUKSJON_MEDLEM", "ARB_REF", "REDUKSJON_ARB_REF")
    }
}
