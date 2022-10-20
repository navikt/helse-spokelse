package no.nav.helse.spokelse

data class Dokumenter(
    val sykmelding: Hendelse,
    val søknad: Hendelse,
    val inntektsmelding: Hendelse?
) {
    init {
        require(sykmelding.type == Dokument.Sykmelding)
        require(søknad.type == Dokument.Søknad)
        inntektsmelding?.also { require(it.type == Dokument.Inntektsmelding) }
    }
}

