package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.utbetalteperioder.Infotrygd
import no.nav.helse.spokelse.utbetalteperioder.Infotrygdperiode
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal class GjenståendeSykedager(
    private val dao: GjenståendeSykedagerDao,
    private val infotrygd: Infotrygd,
    private val spleisApiClient: SpleisApiClient,
    private val idag: () -> LocalDate = { LocalDate.now(ZoneId.of("Europe/Oslo")) },
) {
    internal sealed interface Resultat {
        val grunnlag: Grunnlag

        data class VedtakINyLøsning(
            val gjenståendeDager: Int,
            val vurdertTilOgMed: LocalDate,
        ) : Resultat {
            override val grunnlag = Grunnlag.VEDTAK_I_NY_LØSNING
        }

        data class MaksdatoIInfotrygd(
            val gjenståendeDager: Int,
            val vurdertTilOgMed: LocalDate,
        ) : Resultat {
            override val grunnlag = Grunnlag.MAKSDATO_I_INFOTRYGD
        }

        data object ManglerMaksdatoIInfotrygd : Resultat {
            override val grunnlag = Grunnlag.MANGLER_MAKSDATO_I_INFOTRYGD
        }

        data object IngenSykepengerSiste26Uker : Resultat {
            override val grunnlag = Grunnlag.INGEN_SYKEPENGER_SISTE_26_UKER
        }

        data class SisteUtbetalingIInfotrygd(
            val gjenståendeDager: Int,
            val vurdertTilOgMed: LocalDate,
            val sammenligning: Sammenligning,
        ) : Resultat {
            override val grunnlag = Grunnlag.SISTE_UTBETALING_I_INFOTRYGD
        }
    }

    internal data class Sammenligning(
        val utfall: Utfall,
        val maksdatoFraSpleis: LocalDate,
        val maksdatoIInfotrygd: LocalDate?,
        val avvikIUkedager: Int?,
    ) {
        internal enum class Utfall {
            LIK,
            ULIK,
            MANGLER_MAKSDATO,
        }
    }

    internal suspend fun hent(personidentifikator: Personidentifikator): Resultat {
        val sisteVedtak = dao.sisteVedtak(personidentifikator)
        val infotrygdperioder = infotrygd.hentAllePeriodetyper(personidentifikator, INFOTRYGD_FOM, idag())
        val utbetalingerIInfotrygd = infotrygdperioder.filter { it.erUtbetaling }
        val sisteUtbetaling = utbetalingerIInfotrygd.maxByOrNull { it.tom }

        val sisteDagMedSykepenger = listOfNotNull(sisteVedtak?.vurdertTilOgMed, sisteUtbetaling?.tom).maxOrNull()
        if (sisteDagMedSykepenger == null || harNyRett(sisteDagMedSykepenger)) return Resultat.IngenSykepengerSiste26Uker

        if (sisteVedtak == null) return maksdatoIInfotrygd(checkNotNull(sisteUtbetaling))
        if (sisteUtbetaling == null || sisteUtbetaling.tom <= sisteVedtak.vurdertTilOgMed) {
            return Resultat.VedtakINyLøsning(sisteVedtak.gjenståendeDager, sisteVedtak.vurdertTilOgMed)
        }
        if (harOppholdEtter(sisteVedtak.vurdertTilOgMed, utbetalingerIInfotrygd)) return maksdatoIInfotrygd(sisteUtbetaling)
        val beregning = spleisApiClient.beregnGjenståendeSykedager(personidentifikator, infotrygdperioder)
        return Resultat.SisteUtbetalingIInfotrygd(
            gjenståendeDager = beregning.gjenståendeDager,
            vurdertTilOgMed = beregning.vurdertTilOgMed,
            sammenligning = sammenlign(beregning.maksdato, sisteUtbetaling.maksdato),
        )
    }

    private fun sammenlign(
        maksdatoFraSpleis: LocalDate,
        maksdatoIInfotrygd: LocalDate?,
    ): Sammenligning {
        if (maksdatoIInfotrygd == null) return Sammenligning(Sammenligning.Utfall.MANGLER_MAKSDATO, maksdatoFraSpleis, null, null)
        val utfall = if (maksdatoIInfotrygd == maksdatoFraSpleis) Sammenligning.Utfall.LIK else Sammenligning.Utfall.ULIK
        return Sammenligning(utfall, maksdatoFraSpleis, maksdatoIInfotrygd, ukedagerMellom(maksdatoFraSpleis, maksdatoIInfotrygd))
    }

    // Minst 182 dager fra dagen etter siste dag med sykepenger til og med i dag.
    private fun harNyRett(sisteDagMedSykepenger: LocalDate) = ChronoUnit.DAYS.between(sisteDagMedSykepenger, idag()) >= TILSTREKKELIG_OPPHOLD_I_DAGER

    // Minst 182 dager uten utbetaling et sted mellom siste vedtak og siste utbetaling i Infotrygd.
    private fun harOppholdEtter(
        sisteVedtakTom: LocalDate,
        utbetalingerIInfotrygd: List<Infotrygdperiode>,
    ): Boolean {
        var forrigeUtbetalteDag = sisteVedtakTom
        utbetalingerIInfotrygd.filter { it.tom > sisteVedtakTom }.sortedBy { it.fom }.forEach { utbetaling ->
            val dagerUtenUtbetaling = ChronoUnit.DAYS.between(forrigeUtbetalteDag, utbetaling.fom) - 1
            if (dagerUtenUtbetaling >= TILSTREKKELIG_OPPHOLD_I_DAGER) return true
            forrigeUtbetalteDag = maxOf(forrigeUtbetalteDag, utbetaling.tom)
        }
        return false
    }

    private fun maksdatoIInfotrygd(sisteUtbetaling: Infotrygdperiode): Resultat {
        val maksdato = sisteUtbetaling.maksdato ?: return Resultat.ManglerMaksdatoIInfotrygd
        val vurdertTilOgMed = sisteUtbetaling.tom
        return Resultat.MaksdatoIInfotrygd(
            gjenståendeDager = ukedager(fom = vurdertTilOgMed.plusDays(1), tom = maksdato),
            vurdertTilOgMed = vurdertTilOgMed,
        )
    }

    private companion object {
        private val INFOTRYGD_FOM = LocalDate.of(2000, 1, 1)

        // 26 uker, som TILSTREKKELIG_OPPHOLD_I_SYKEDAGER i maksdatoberegningen i spleis.
        private const val TILSTREKKELIG_OPPHOLD_I_DAGER = 182
        private val EN_MANDAG = LocalDate.of(1970, 1, 5)

        // Mandag–fredag fra og med fom til og med tom, minst 0. Helligdager telles med, slik spleis gjør.
        private fun ukedager(
            fom: LocalDate,
            tom: LocalDate,
        ): Int {
            if (tom < fom) return 0
            return (ukedagerFraEnMandagTil(tom.plusDays(1)) - ukedagerFraEnMandagTil(fom)).toInt()
        }

        private fun ukedagerMellom(
            fra: LocalDate,
            til: LocalDate,
        ): Int = (ukedagerFraEnMandagTil(til.plusDays(1)) - ukedagerFraEnMandagTil(fra.plusDays(1))).toInt()

        private fun ukedagerFraEnMandagTil(dato: LocalDate): Long {
            val dager = ChronoUnit.DAYS.between(EN_MANDAG, dato)
            return Math.floorDiv(dager, 7) * 5 + minOf(Math.floorMod(dager, 7), 5)
        }
    }
}
