package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.tbdutbetaling.TbdUtbtalingApi
import java.time.LocalDate


internal class SpleisPerioder(private val spleis: Spleis, private val personidentifikatorer: Set<Personidentifikator>, private val fom: LocalDate, private val tom: LocalDate): Iterable<SpøkelsePeriode> {
    override fun iterator(): Iterator<SpøkelsePeriode> {
        return spleis.hent(personidentifikatorer, fom, tom).iterator()
    }
}

internal class Spleis(private val tbdUtbtalingApi: TbdUtbtalingApi) {
    fun hent(personidentifikatorer: Set<Personidentifikator>, tidligsteSluttdato: LocalDate, senesteStartdato: LocalDate): List<SpøkelsePeriode> {
        return personidentifikatorer
            .associateWith { tbdUtbtalingApi.utbetalinger(it.toString(), tidligsteSluttdato, senesteStartdato) }
            .mapValues { (personidentifikator, utbetalinger) ->
                utbetalinger.flatMap { utbetaling ->
                    val førsteOppdragMedLinjer = utbetaling.arbeidsgiverOppdrag?.takeUnless { it.utbetalingslinjer.isEmpty() } ?: utbetaling.personOppdrag!!
                    førsteOppdragMedLinjer.utbetalingslinjer.map { utbetalingslinje ->
                        // TODO: Vi mangler organisasjonsnummer 🫠
                        SpøkelsePeriode(personidentifikator, utbetalingslinje.fom, utbetalingslinje.tom, utbetalingslinje.grad.toInt(), null, "Spleis")
                    }
                }
            }.values.flatten()
    }
}
