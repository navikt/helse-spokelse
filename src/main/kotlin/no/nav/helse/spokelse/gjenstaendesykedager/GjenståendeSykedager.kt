package no.nav.helse.spokelse.gjenstaendesykedager

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spokelse.felles.Infotrygdperiode
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.sykepenger.libs.logging.navngittLogger
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val logger = navngittLogger("no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedager")

internal enum class Grunnlag {
    VEDTAK_I_NY_LØSNING,
    SISTE_UTBETALING_I_INFOTRYGD,
    MAKSDATO_I_INFOTRYGD,
    MANGLER_MAKSDATO_I_INFOTRYGD,
    INGEN_SYKEPENGER_SISTE_26_UKER,
}

internal class GjenståendeSykedager(
    private val dao: GjenståendeSykedagerDao,
    private val infotrygdperioder: Infotrygdperioder,
    private val maksdatoberegning: Maksdatoberegning,
    private val meterRegistry: MeterRegistry,
    private val idag: () -> LocalDate = { LocalDate.now(ZoneId.of("Europe/Oslo")) },
) {
    @ConsistentCopyVisibility
    internal data class Resultat private constructor(
        val grunnlag: Grunnlag,
        val gjenståendeDager: Int?,
        val vurdertTilOgMed: LocalDate?,
    ) {
        internal companion object {
            internal fun vedtakINyLøsning(
                gjenståendeDager: Int,
                vurdertTilOgMed: LocalDate,
            ) = Resultat(Grunnlag.VEDTAK_I_NY_LØSNING, gjenståendeDager, vurdertTilOgMed)

            internal fun maksdatoIInfotrygd(
                gjenståendeDager: Int,
                vurdertTilOgMed: LocalDate,
            ) = Resultat(Grunnlag.MAKSDATO_I_INFOTRYGD, gjenståendeDager, vurdertTilOgMed)

            internal fun sisteUtbetalingIInfotrygd(
                gjenståendeDager: Int,
                vurdertTilOgMed: LocalDate,
            ) = Resultat(Grunnlag.SISTE_UTBETALING_I_INFOTRYGD, gjenståendeDager, vurdertTilOgMed)

            internal val manglerMaksdatoIInfotrygd = Resultat(Grunnlag.MANGLER_MAKSDATO_I_INFOTRYGD, null, null)
            internal val ingenSykepengerSiste26Uker = Resultat(Grunnlag.INGEN_SYKEPENGER_SISTE_26_UKER, null, null)
        }
    }

    private data class Sammenligning(
        val utfall: Utfall,
        val maksdatoFraSpleis: LocalDate,
        val maksdatoIInfotrygd: LocalDate?,
        val avvikIUkedager: Int?,
    ) {
        enum class Utfall {
            LIK,
            ULIK,
            MANGLER_MAKSDATO,
        }
    }

    internal suspend fun hent(personidentifikator: Personidentifikator): Resultat {
        val sisteVedtak = dao.sisteVedtak(personidentifikator)
        val perioderIInfotrygd = infotrygdperioder.hentAllePeriodetyper(personidentifikator, INFOTRYGD_FOM, idag())
        val utbetalingerIInfotrygd = perioderIInfotrygd.filter { it.erUtbetaling }
        val sisteUtbetaling = utbetalingerIInfotrygd.maxByOrNull { it.tom }

        val sisteDagMedSykepenger = listOfNotNull(sisteVedtak?.vurdertTilOgMed, sisteUtbetaling?.tom).maxOrNull()
        if (sisteDagMedSykepenger == null || harNyRett(sisteDagMedSykepenger)) return Resultat.ingenSykepengerSiste26Uker

        if (sisteVedtak == null) return maksdatoIInfotrygd(checkNotNull(sisteUtbetaling))
        if (sisteUtbetaling == null || sisteUtbetaling.tom <= sisteVedtak.vurdertTilOgMed) {
            return Resultat.vedtakINyLøsning(sisteVedtak.gjenståendeDager, sisteVedtak.vurdertTilOgMed)
        }
        if (harOppholdEtter(sisteVedtak.vurdertTilOgMed, utbetalingerIInfotrygd)) return maksdatoIInfotrygd(sisteUtbetaling)
        val beregning = maksdatoberegning.beregnGjenståendeSykedager(personidentifikator, perioderIInfotrygd)
        rapporterSammenligningMedInfotrygd(personidentifikator, sammenlign(beregning.maksdato, sisteUtbetaling.maksdato))
        return Resultat.sisteUtbetalingIInfotrygd(beregning.gjenståendeDager, beregning.vurdertTilOgMed)
    }

    private fun rapporterSammenligningMedInfotrygd(
        personidentifikator: Personidentifikator,
        sammenligning: Sammenligning,
    ) {
        meterRegistry.counter("gjenstaende_sykedager_sammenligning_med_infotrygd", "utfall", sammenligning.utfall.name).increment()
        logger.info(
            "Sammenligner maksdatoen fra spleis-api med Infotrygds maksdato",
            "fødselsnummer" to personidentifikator.toString(),
            "sammenligningMedInfotrygd" to sammenligning.utfall.name,
            "maksdatoFraSpleis" to sammenligning.maksdatoFraSpleis.toString(),
            "maksdatoIInfotrygd" to sammenligning.maksdatoIInfotrygd?.toString(),
            "avvikIUkedager" to sammenligning.avvikIUkedager?.toString(),
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
        val maksdato = sisteUtbetaling.maksdato ?: return Resultat.manglerMaksdatoIInfotrygd
        val vurdertTilOgMed = sisteUtbetaling.tom
        return Resultat.maksdatoIInfotrygd(
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
