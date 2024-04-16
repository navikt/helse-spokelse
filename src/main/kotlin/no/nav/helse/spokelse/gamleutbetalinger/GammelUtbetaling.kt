package no.nav.helse.spokelse.gamleutbetalinger

import no.nav.helse.spokelse.FpVedtak
import no.nav.helse.spokelse.Utbetalingsperiode
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import java.time.LocalDate
import java.time.LocalDateTime

data class GammelUtbetaling (
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    private val fagsystemId: String,
    private val utbetaltTidspunkt: LocalDateTime,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val grad: Int,
    private val kilde: String
) {

    private fun somFpUtbetalingsperiode() = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        grad = grad.toDouble()
    )

    private fun somSpøkelsePeriode() = SpøkelsePeriode(
        personidentifikator = Personidentifikator(fødselsnummer),
        fom = fom,
        tom = tom,
        grad = grad,
        organisasjonsnummer = organisasjonsnummer,
        tags = setOf("Spleis", kilde)
    )

    internal companion object {
        internal fun Collection<GammelUtbetaling>.somFpVedtak() = groupBy { it.fagsystemId }.mapValues { (fagsystemId, utbetalinger) ->
            FpVedtak(
                vedtaksreferanse = fagsystemId,
                vedtattTidspunkt = utbetalinger.minOf { it.utbetaltTidspunkt },
                utbetalinger = utbetalinger.sortedBy { it.fom }.map(GammelUtbetaling::somFpUtbetalingsperiode)
            )
        }.values

        internal fun Collection<GammelUtbetaling>.somSpøkelsePerioder() = map(GammelUtbetaling::somSpøkelsePeriode).toSet()
    }
}
