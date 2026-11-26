package no.nav.helse.spokelse.gamleutbetalinger

import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.grunnlag.FpVedtak
import no.nav.helse.spokelse.grunnlag.Utbetalingsperiode
import no.nav.helse.spokelse.personoversikt.Mottaker
import no.nav.helse.spokelse.personoversikt.SpeilV1Linje
import no.nav.helse.spokelse.personoversikt.SpeilV2Linje
import no.nav.helse.spokelse.personoversikt.SpeilV3Linje
import no.nav.helse.spokelse.personoversikt.UtbetaltLinje
import java.time.LocalDate
import java.time.LocalDateTime

// tag er navnet v1 bruker i tags, og verdien i kilde-kolonnen i spørringen i GamleUtbetalingerDao.
internal enum class GammelKilde(
    internal val tag: String,
) {
    GAMLE_UTBETALINGER("GamleUtbetalinger"),
    OLD_VEDTAK_OG_OLD_UTBETALING("OldVedtakOgOldUtbetaling"),
    VEDTAK_OPPDRAG_OG_UTBETALING("VedtakOppdragOgUtbetaling"),
}

internal data class GammelUtbetaling(
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    private val fagsystemId: String,
    private val utbetaltTidspunkt: LocalDateTime,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val grad: Int,
    private val kilde: GammelKilde,
    private val mottaker: Mottaker?,
    private val dagsats: Int?,
    private val beløp: Int?,
) {
    private fun somFpUtbetalingsperiode() =
        Utbetalingsperiode(
            fom = fom,
            tom = tom,
            grad = grad.toDouble(),
        )

    private fun somSpøkelsePeriode() =
        SpøkelsePeriode(
            personidentifikator = Personidentifikator(fødselsnummer),
            fom = fom,
            tom = tom,
            grad = grad,
            organisasjonsnummer = organisasjonsnummer,
            tags = setOf("Spleis", kilde.tag),
        )

    private fun somUtbetaltLinje(): UtbetaltLinje {
        val personidentifikator = Personidentifikator(fødselsnummer)

        fun mottaker() = checkNotNull(mottaker) { "Mangler mottaker for gammel utbetaling fra ${kilde.tag}" }

        fun dagsats() = checkNotNull(dagsats) { "Mangler dagsats for gammel utbetaling fra ${kilde.tag}" }

        fun beløp() = checkNotNull(beløp) { "Mangler beløp for gammel utbetaling fra ${kilde.tag}" }
        return when (kilde) {
            GammelKilde.GAMLE_UTBETALINGER -> SpeilV1Linje(personidentifikator, organisasjonsnummer, mottaker(), fom, tom, grad)
            GammelKilde.OLD_VEDTAK_OG_OLD_UTBETALING -> SpeilV2Linje(personidentifikator, organisasjonsnummer, fom, tom, grad, dagsats(), beløp())
            GammelKilde.VEDTAK_OPPDRAG_OG_UTBETALING -> SpeilV3Linje(personidentifikator, organisasjonsnummer, mottaker(), fom, tom, grad, dagsats(), beløp())
        }
    }

    internal companion object {
        internal fun Collection<GammelUtbetaling>.somFpVedtak() =
            groupBy { it.fagsystemId }
                .mapValues { (fagsystemId, utbetalinger) ->
                    FpVedtak(
                        vedtaksreferanse = fagsystemId,
                        vedtattTidspunkt = utbetalinger.minOf { it.utbetaltTidspunkt },
                        utbetalinger = utbetalinger.sortedBy { it.fom }.map(GammelUtbetaling::somFpUtbetalingsperiode),
                    )
                }.values

        internal fun Collection<GammelUtbetaling>.somSpøkelsePerioder() = map(GammelUtbetaling::somSpøkelsePeriode).toSet()

        internal fun Collection<GammelUtbetaling>.somUtbetalteLinjer() = map(GammelUtbetaling::somUtbetaltLinje)
    }
}
