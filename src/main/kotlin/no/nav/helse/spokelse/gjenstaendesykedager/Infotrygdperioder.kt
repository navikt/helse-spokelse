package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.felles.Infotrygdperiode
import no.nav.helse.spokelse.felles.Personidentifikator
import java.time.LocalDate

internal interface Infotrygdperioder {
    suspend fun hentAllePeriodetyper(
        personidentifikator: Personidentifikator,
        fom: LocalDate,
        tom: LocalDate,
    ): List<Infotrygdperiode>
}
