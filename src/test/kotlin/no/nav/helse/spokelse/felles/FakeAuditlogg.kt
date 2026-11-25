package no.nav.helse.spokelse.felles

import no.nav.helse.spokelse.Auditlogg
import no.nav.helse.spokelse.Saksbehandler

internal class FakeAuditlogg : Auditlogg {
    internal enum class Utfall { LEST, AVSLÅTT }

    internal data class Innslag(
        val navIdent: String,
        val personidentifikator: Personidentifikator,
        val utfall: Utfall,
    )

    val innslag = mutableListOf<Innslag>()

    override fun lest(
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
    ) {
        innslag += Innslag(saksbehandler.navIdent, personidentifikator, Utfall.LEST)
    }

    override fun avslått(
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
    ) {
        innslag += Innslag(saksbehandler.navIdent, personidentifikator, Utfall.AVSLÅTT)
    }
}
