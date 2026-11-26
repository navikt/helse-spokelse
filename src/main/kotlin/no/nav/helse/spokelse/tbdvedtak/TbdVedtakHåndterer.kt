package no.nav.helse.spokelse.tbdvedtak

import kotliquery.sessionOf
import no.nav.helse.spokelse.tbdvedtak.TbdVedtak.Companion.vedtakFattetOrNull
import no.nav.sykepenger.libs.logging.loggInfo
import tools.jackson.databind.JsonNode
import javax.sql.DataSource

// Lagrer vedtak som er fattet. Annulleringer ignoreres, fordi tbd.utbetaling fjerner linjene som annulleres.
internal class TbdVedtakHåndterer(
    private val dataSource: () -> DataSource,
) {
    internal fun håndter(json: JsonNode) {
        val vedtak = json.vedtakFattetOrNull() ?: return
        sessionOf(dataSource()).use { session -> TbdVedtakDao(session).lagre(vedtak, json) }
        loggInfo("Lagret vedtak fra $TOPIC", "fødselsnummer" to vedtak.fødselsnummer)
    }

    internal companion object {
        internal const val TOPIC = "tbd.vedtak"
    }
}
