package no.nav.helse.spokelse.utbetalteperioder

class Personidentifikator(private val id: String) {
    init {
        check(id.matches("\\d{11}".toRegex())) { "Ugyldig personidentifikator" }
    }

    override fun toString() = id
    override fun equals(other: Any?) = other is Personidentifikator && other.id == id
    override fun hashCode() = id.hashCode()
}
