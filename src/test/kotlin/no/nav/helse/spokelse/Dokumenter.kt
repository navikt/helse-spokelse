package no.nav.helse.spokelse

import java.util.*

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

enum class Dokument {
    Sykmelding, Inntektsmelding, Søknad
}

data class Hendelse(
    val dokumentId: UUID,
    val hendelseId: UUID,
    val type: Dokument
)
