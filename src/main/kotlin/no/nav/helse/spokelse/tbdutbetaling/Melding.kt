package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

internal class Melding(
    private val melding: String,
    internal val meldingSendt: LocalDateTime,
    internal val type: String,
    internal val fødselsnummer: String) {
    internal constructor(jsonNode: JsonNode, meldingSendt: LocalDateTime): this(jsonNode.toString(), meldingSendt, jsonNode.event, jsonNode.fødselsnummer)
    override fun toString() = melding

    internal companion object {
        internal fun JsonNode.melding(meldingSendt: LocalDateTime) = Melding(this, meldingSendt)
        internal val JsonNode.event get() = path("event").asText()
        internal val JsonNode.fødselsnummer get() = get("fødselsnummer").asText()
        internal val JsonNode.erUtbetaling get() = event == "utbetaling_utbetalt"
        internal val JsonNode.erAnnullering get() = event == "utbetaling_annullert"
        internal val JsonNode.erUtbetalingUtenUtbetaling get() = event == "utbetaling_uten_utbetaling"
    }
}
