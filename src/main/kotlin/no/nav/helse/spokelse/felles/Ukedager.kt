package no.nav.helse.spokelse.felles

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val EN_MANDAG = LocalDate.of(1970, 1, 5)

// Dagene fra mandag til fredag fra og med fom til og med tom, også helligdager. Minst 0.
// Spleis teller på samme måte i `ukedager` i sykepenger-primitiver (Dato.kt), men der er tom ikke med.
internal fun ukedager(
    fom: LocalDate,
    tom: LocalDate,
): Int {
    if (tom < fom) return 0
    return (ukedagerFraEnMandagTil(tom.plusDays(1)) - ukedagerFraEnMandagTil(fom)).toInt()
}

private fun ukedagerFraEnMandagTil(dato: LocalDate): Long {
    val dager = ChronoUnit.DAYS.between(EN_MANDAG, dato)
    return Math.floorDiv(dager, 7) * 5 + minOf(Math.floorMod(dager, 7), 5)
}
