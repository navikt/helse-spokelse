package no.nav.helse.spokelse.gamleutbetalinger

import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.grunnlag.FpVedtak
import no.nav.helse.spokelse.grunnlag.Utbetalingsperiode
import no.nav.helse.spokelse.personoversikt.Mottaker
import no.nav.helse.spokelse.personoversikt.UtbetaltLinje
import no.nav.helse.spokelse.personoversikt.Versjon
import java.time.LocalDate
import java.time.LocalDateTime

// tag er navnet v1 bruker i tags, og verdien i kilde-kolonnen i spørringen i GamleUtbetalingerDao.
internal enum class GammelKilde(
    internal val tag: String,
    internal val versjon: Versjon,
) {
    GAMLE_UTBETALINGER("GamleUtbetalinger", Versjon.SPEIL_V1),
    OLD_VEDTAK_OG_OLD_UTBETALING("OldVedtakOgOldUtbetaling", Versjon.SPEIL_V2),
    VEDTAK_OPPDRAG_OG_UTBETALING("VedtakOppdragOgUtbetaling", Versjon.SPEIL_V3),
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

    private fun somUtbetaltLinje() =
        UtbetaltLinje(
            versjon = kilde.versjon,
            personidentifikator = Personidentifikator(fødselsnummer),
            organisasjonsnummer = organisasjonsnummer,
            mottaker = mottaker,
            fom = fom,
            tom = tom,
            grad = grad,
            usikkerGrad = false,
        )

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
