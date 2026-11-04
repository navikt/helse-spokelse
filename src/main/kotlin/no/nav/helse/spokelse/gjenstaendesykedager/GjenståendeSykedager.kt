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
        data class VedtakINyLøsning(
            val gjenståendeDager: Int,
            val vurdertTilOgMed: LocalDate,
        ) : Resultat

        data object IngenVedtak : Resultat

        data object SisteUtbetalingIInfotrygd : Resultat
    }

    internal suspend fun hent(personidentifikator: Personidentifikator): Resultat {
        val sisteVedtak = dao.sisteVedtak(personidentifikator.toString()) ?: return Resultat.IngenVedtak
        if (harSenereUtbetalingIInfotrygd(personidentifikator, sisteVedtak.vurdertTilOgMed)) return Resultat.SisteUtbetalingIInfotrygd
        return Resultat.VedtakINyLøsning(sisteVedtak.gjenståendeDager, sisteVedtak.vurdertTilOgMed)
    }

    private suspend fun harSenereUtbetalingIInfotrygd(
        personidentifikator: Personidentifikator,
        vurdertTilOgMed: LocalDate,
    ): Boolean {
        val fom = vurdertTilOgMed.plusDays(1)
        val tom = idag()
        if (fom > tom) return false
        return infotrygd.hent(setOf(personidentifikator), fom, tom).any { it.tom > vurdertTilOgMed }
    }
}
