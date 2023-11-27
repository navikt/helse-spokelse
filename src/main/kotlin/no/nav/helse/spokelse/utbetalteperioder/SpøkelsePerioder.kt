package no.nav.helse.spokelse.utbetalteperioder

import java.time.LocalDate

class SpøkelsePeriode(
    val personidentifikator: Personidentifikator,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val organisasjonsnummer: String?,
    val kilde: String
)
