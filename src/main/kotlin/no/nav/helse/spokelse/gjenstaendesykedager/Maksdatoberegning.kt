package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.felles.Infotrygdperiode
import no.nav.helse.spokelse.felles.Personidentifikator
import java.time.LocalDate

internal interface Maksdatoberegning {
    suspend fun beregnGjenståendeSykedager(
        personidentifikator: Personidentifikator,
        infotrygdperioder: List<Infotrygdperiode>,
    ): Beregning

    data class Beregning(
        val gjenståendeDager: Int,
        val vurdertTilOgMed: LocalDate,
        val maksdato: LocalDate,
    )
}
