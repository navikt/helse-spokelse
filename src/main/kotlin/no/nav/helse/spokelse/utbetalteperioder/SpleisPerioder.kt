package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.gamlevedtak.HentVedtakDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import java.time.LocalDate


internal class SpleisPerioder(private val spleis: Spleis, private val personidentifikatorer: Set<Personidentifikator>, private val fom: LocalDate, private val tom: LocalDate): Iterable<SpøkelsePeriode> {
    override fun iterator(): Iterator<SpøkelsePeriode> {
        return spleis.hent(personidentifikatorer, fom, tom).iterator()
    }
}

internal class Spleis(private val tbdUtbetalingApi: TbdUtbetalingApi, private val hentVedtakDao: HentVedtakDao) {
    fun hent(personidentifikatorer: Set<Personidentifikator>, tidligsteSluttdato: LocalDate, senesteStartdato: LocalDate): List<SpøkelsePeriode> {
        return personidentifikatorer
            .associateWith { tbdUtbetalingApi.utbetalinger(it.toString(), tidligsteSluttdato, senesteStartdato) }
            .mapValues { (personidentifikator, utbetalinger) ->
                utbetalinger.flatMap { utbetaling ->
                    val førsteOppdragMedLinjer = utbetaling.arbeidsgiverOppdrag?.takeUnless { it.utbetalingslinjer.isEmpty() } ?: utbetaling.personOppdrag!!
                    førsteOppdragMedLinjer.utbetalingslinjer.map { utbetalingslinje ->
                        SpøkelsePeriode(personidentifikator, utbetalingslinje.fom, utbetalingslinje.tom, utbetalingslinje.grad.toInt(), utbetaling.organisasjonsnummer, setOf("Spleis", "TbdUtbetaling"))
                    }
                }
            }.mapValues { (personidentifikator, spøkelsePerioder) ->
                spøkelsePerioder + hentVedtakDao.hentSpøkelsePerioder(personidentifikator.toString(), tidligsteSluttdato, senesteStartdato)
            }.values.flatten().slåSammen()
    }

    internal companion object {
        private data class Grupperingsnøkkel(val personidentifikator: Personidentifikator, val fom: LocalDate, val grad: Int, val organisasjonsnummer: String)
        internal fun List<SpøkelsePeriode>.slåSammen(): List<SpøkelsePeriode> {
            val (medOrganisasjonsnummer, utenOrganisasjonsnummer) = partition { it.organisasjonsnummer != null }

            return medOrganisasjonsnummer.groupBy { Grupperingsnøkkel(it.personidentifikator, it.fom, it.grad, it.organisasjonsnummer!!) }
                .mapValues { (_, gruppertePerioder) -> SpøkelsePeriode(
                    personidentifikator = gruppertePerioder.first().personidentifikator,
                    fom = gruppertePerioder.first().fom,
                    tom = gruppertePerioder.maxOf { it.tom },
                    grad = gruppertePerioder.first().grad,
                    organisasjonsnummer = gruppertePerioder.first().organisasjonsnummer,
                    tags = gruppertePerioder.flatMap { it.tags }.toSet()
                ) }
                .values.toList() + utenOrganisasjonsnummer
        }
    }
}
