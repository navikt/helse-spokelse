package no.nav.helse.spokelse.utbetalteperioder

import java.time.LocalDate

class SpøkelsePeriode(
    val personidentifikator: Personidentifikator,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val organisasjonsnummer: String?,
    val tags: Set<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpøkelsePeriode

        if (personidentifikator != other.personidentifikator) return false
        if (fom != other.fom) return false
        if (tom != other.tom) return false
        if (grad != other.grad) return false
        if (organisasjonsnummer != other.organisasjonsnummer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = personidentifikator.hashCode()
        result = 31 * result + fom.hashCode()
        result = 31 * result + tom.hashCode()
        result = 31 * result + grad
        result = 31 * result + (organisasjonsnummer?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "SpøkelsePeriode(personidentifikator=$personidentifikator, fom=$fom, tom=$tom, grad=$grad, organisasjonsnummer=$organisasjonsnummer, tags=$tags)"
    }
}
