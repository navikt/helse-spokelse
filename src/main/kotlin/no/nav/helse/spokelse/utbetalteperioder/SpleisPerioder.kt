package no.nav.helse.spokelse.utbetalteperioder

import java.time.LocalDate


internal class SpleisPerioder(private val spleis: Spleis, private val personidentifikatorer: Set<Personidentifikator>, private val fom: LocalDate, private val tom: LocalDate): Iterable<SpøkelsePeriode> {
    override fun iterator(): Iterator<SpøkelsePeriode> {
        return spleis.hent(personidentifikatorer, fom, tom).iterator()
    }
}

internal class Spleis {
    fun hent(personidentifikatorer: Set<Personidentifikator>, tidligsteSluttdato: LocalDate, senesteStartdato: LocalDate): List<SpøkelsePeriode> {
        return emptyList()
    }
}
