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
        val status: Status

        data class VedtakINyLøsning(
            val gjenståendeDager: Int,
            val vurdertTilOgMed: LocalDate,
        ) : Resultat {
            override val status = Status.VEDTAK_I_NY_LØSNING
        }

        data object IngenVedtak : Resultat {
            override val status = Status.INGEN_VEDTAK
        }

        data object SisteUtbetalingIInfotrygd : Resultat {
            override val status = Status.SISTE_UTBETALING_I_INFOTRYGD
        }
    }

    internal suspend fun hent(personidentifikator: Personidentifikator): Resultat {
        val sisteVedtak = dao.sisteVedtak(personidentifikator)
        if (sisteVedtak == null) {
            if (harUtbetalingIInfotrygd(personidentifikator, fom = INFOTRYGD_FOM)) return Resultat.SisteUtbetalingIInfotrygd
            return Resultat.IngenVedtak
        }
        if (harUtbetalingIInfotrygd(personidentifikator, fom = sisteVedtak.vurdertTilOgMed.plusDays(1))) return Resultat.SisteUtbetalingIInfotrygd
        return Resultat.VedtakINyLøsning(sisteVedtak.gjenståendeDager, sisteVedtak.vurdertTilOgMed)
    }

    private suspend fun harUtbetalingIInfotrygd(
        personidentifikator: Personidentifikator,
        fom: LocalDate,
    ): Boolean {
        val tom = idag()
        if (fom > tom) return false
        return infotrygd.hent(setOf(personidentifikator), fom, tom).any { it.tom >= fom }
    }

    private companion object {
        // Har personen ingen vedtak i ny løsning, gir alle utbetalinger i Infotrygd SISTE_UTBETALING_I_INFOTRYGD, også gamle.
        private val INFOTRYGD_FOM = LocalDate.of(2000, 1, 1)
    }
}
