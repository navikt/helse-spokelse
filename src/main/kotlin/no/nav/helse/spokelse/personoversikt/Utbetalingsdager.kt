package no.nav.helse.spokelse.personoversikt

import no.nav.helse.spokelse.felles.Personidentifikator
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate

// En ferierad fra Infotrygd. Ferien hører til sykefraværstilfellet og gjelder alle arbeidsgiverne i det.
internal data class Infotrygdferie(
    val personidentifikator: Personidentifikator,
    val skjæringstidspunkt: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
)

// Dagene fra mandag til fredag i fom–tom, også helligdager, minus feriedagene i samme sykefraværstilfelle.
// Infotrygd deler vanligvis utbetalingen rundt ferien, men en utbetalingsrad kan ha en ferierad inni seg, og da er feriedagene ikke utbetalt.
internal fun utbetalingsdager(
    personidentifikator: Personidentifikator,
    skjæringstidspunkt: LocalDate,
    fom: LocalDate,
    tom: LocalDate,
    ferie: List<Infotrygdferie>,
): Int {
    val feriedager =
        ferie
            .filter { it.personidentifikator == personidentifikator && it.skjæringstidspunkt == skjæringstidspunkt }
            .flatMap { it.fom.datesUntil(it.tom.plusDays(1)).toList() }
            .toSet()
    return fom
        .datesUntil(tom.plusDays(1))
        .filter { it.dayOfWeek != SATURDAY && it.dayOfWeek != SUNDAY && it !in feriedager }
        .count()
        .toInt()
}
