package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.utbetalteperioder.Infotrygd
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import java.time.LocalDate

internal class GjenståendeSykedager(
    private val dao: GjenståendeSykedagerDao,
    private val infotrygd: Infotrygd,
    private val idag: () -> LocalDate = LocalDate::now,
) {
    internal sealed interface Resultat {
        data class Svar(
            val gjenståendeSykedager: Int,
            val gjelderTilOgMed: LocalDate,
        ) : Resultat

        data object IngenVedtak : Resultat

        data object SisteUtbetalingIInfotrygd : Resultat
    }

    internal suspend fun hent(personidentifikator: Personidentifikator): Resultat {
        val sisteVedtak = dao.sisteVedtak(personidentifikator.toString()) ?: return Resultat.IngenVedtak
        if (harSenereUtbetalingIInfotrygd(personidentifikator, sisteVedtak.gjelderTilOgMed)) return Resultat.SisteUtbetalingIInfotrygd
        return Resultat.Svar(sisteVedtak.gjenståendeSykedager, sisteVedtak.gjelderTilOgMed)
    }

    private suspend fun harSenereUtbetalingIInfotrygd(
        personidentifikator: Personidentifikator,
        gjelderTilOgMed: LocalDate,
    ): Boolean {
        val fom = gjelderTilOgMed.plusDays(1)
        val tom = idag()
        if (fom > tom) return false
        return infotrygd.hent(setOf(personidentifikator), fom, tom).any { it.tom > gjelderTilOgMed }
    }
}
