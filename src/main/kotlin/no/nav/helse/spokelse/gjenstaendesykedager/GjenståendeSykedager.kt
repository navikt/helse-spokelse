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

    internal suspend fun hent(fødselsnummer: String): Resultat {
        val sisteVedtak = dao.sisteVedtak(fødselsnummer) ?: return Resultat.IngenVedtak
        if (harSenereUtbetalingIInfotrygd(fødselsnummer, sisteVedtak.gjelderTilOgMed)) return Resultat.SisteUtbetalingIInfotrygd
        return Resultat.Svar(sisteVedtak.gjenståendeSykedager, sisteVedtak.gjelderTilOgMed)
    }

    private suspend fun harSenereUtbetalingIInfotrygd(
        fødselsnummer: String,
        gjelderTilOgMed: LocalDate,
    ): Boolean {
        val fom = gjelderTilOgMed.plusDays(1)
        val tom = idag()
        if (fom > tom) return false
        return infotrygd.hent(setOf(Personidentifikator(fødselsnummer)), fom, tom).any { it.tom > gjelderTilOgMed }
    }
}
