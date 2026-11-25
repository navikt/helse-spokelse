package no.nav.helse.spokelse.felles

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat

// Gir tilgang til alle personer, med mindre testen har bestemt noe annet for personen.
internal class FakeTilgangsmaskinen : PopulasjonstilgangskontrollProvider {
    private val svar = mutableMapOf<String, () -> TilgangskontrollResultat>()
    val accessTokens = mutableListOf<String>()

    fun svar(
        personidentifikator: String,
        resultat: TilgangskontrollResultat,
    ) {
        svar[personidentifikator] = { resultat }
    }

    fun feiler(
        personidentifikator: String,
        feil: Exception,
    ) {
        svar[personidentifikator] = { throw feil }
    }

    override fun kontrollerKjerneTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat {
        accessTokens += accessToken
        return svar[fødselsnummer]?.invoke() ?: TilgangskontrollResultat.Ok
    }

    override fun kontrollerKomplettTilgang(
        accessToken: String,
        fødselsnummer: String,
    ) = error("Spøkelse skal bare sjekke kjerneregelsettet")

    override fun kontrollerKjerneTilgangForAnsatt(
        ansattId: String,
        fødselsnummer: String,
    ) = error("Spøkelse skal bruke OBO-tokenet til saksbehandleren")
}
