package no.nav.helse.spokelse

import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.sykepenger.libs.logging.navngittLogger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

internal interface Auditlogg {
    fun lest(
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
    )

    fun avslått(
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
    )
}

// Samme format som i Spesialist og Sporhund. CEF-headeren står i logback.xml, og auditLogger skriver bare til audit.nais.
// Linjen kopieres til Team Logs, fordi teamet ikke kan lese audit-loggen selv.
internal object Auditlogger : Auditlogg {
    private val auditLog = LoggerFactory.getLogger("auditLogger")
    private val logger = navngittLogger("no.nav.helse.spokelse.Auditlogger")

    override fun lest(
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
    ) = logg(Level.INFO, saksbehandler, personidentifikator, "")

    override fun avslått(
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
    ) = logg(Level.WARN, saksbehandler, personidentifikator, " flexString1=Deny")

    private fun logg(
        level: Level,
        saksbehandler: Saksbehandler,
        personidentifikator: Personidentifikator,
        suffix: String,
    ) {
        val melding = "end=${System.currentTimeMillis()} suid=${saksbehandler.navIdent} duid=$personidentifikator$suffix"
        auditLog.atLevel(level).log(melding)
        logger.debug("Audit-logget oppslag", "level" to level.name, "auditmelding" to melding)
    }
}
