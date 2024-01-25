package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.spokelse.gamlevedtak.HentVedtakDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somSpøkelsePerioder
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
            .mapValues { (_, utbetalinger) ->
                utbetalinger.somSpøkelsePerioder()
            }.mapValues { (personidentifikator, spøkelsePerioder) ->
                spøkelsePerioder + hentVedtakDao.hentSpøkelsePerioder(personidentifikator.toString(), tidligsteSluttdato, senesteStartdato)
            }.values.flatten().slåSammen()
    }

    internal companion object {
        private data class Grupperingsnøkkel(val personidentifikator: Personidentifikator, val grad: Int, val organisasjonsnummer: String)
        internal fun List<SpøkelsePeriode>.slåSammen(): List<SpøkelsePeriode> {
            val (medOrganisasjonsnummer, utenOrganisasjonsnummer) = partition { it.organisasjonsnummer != null }

            return medOrganisasjonsnummer.groupBy { Grupperingsnøkkel(it.personidentifikator, it.grad, it.organisasjonsnummer!!) }
                .mapValues { (_, gruppertePerioder) ->
                    val første = gruppertePerioder.first()
                    val sammenhengendePerioder = gruppertePerioder.map { it.periode }.grupperSammenhengendePerioder()
                    val tags = gruppertePerioder.associate { it.periode to it.tags }

                    sammenhengendePerioder.map { sammenhengendePeriode ->
                        SpøkelsePeriode(
                            personidentifikator = første.personidentifikator,
                            fom = sammenhengendePeriode.start,
                            tom = sammenhengendePeriode.endInclusive,
                            grad = første.grad,
                            organisasjonsnummer = første.organisasjonsnummer,
                            tags = tags.filter { it.key.overlapperMed(sammenhengendePeriode) }.values.flatten().toSet()
                        )
                    }
                }.values.flatten() + utenOrganisasjonsnummer
        }
    }
}
