package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje.Companion.utbetalingslinjerOrNull

internal data class Oppdrag(
    internal val fagsystemId: String,
    internal val utbetalingslinjer: List<Utbetalingslinje>
)

internal data class Utbetaling(
    internal val fødselsnummer: String,
    internal val gjenståendeSykedager: Int,
    internal val arbeidsgiverOppdrag: Oppdrag?,
    internal val personOppdrag: Oppdrag?
) {
    init {
        require(arbeidsgiverOppdrag != null || personOppdrag != null) {
            "Hverken arbeidsgiverOppdrag eller personOppdrag er satt."
        }
    }

    internal companion object {
        internal fun JsonMessage.utbetaling(): Utbetaling {
            val arbeidsgiverOppdrag = utbetalingslinjerOrNull("arbeidsgiverOppdrag.utbetalingslinjer")?.let { utbetalingslinjer -> Oppdrag(
                fagsystemId = get("arbeidsgiverOppdrag.fagsystemId").asText(),
                utbetalingslinjer = utbetalingslinjer
            )}

            val personOppdrag = utbetalingslinjerOrNull("personOppdrag.utbetalingslinjer")?.let { utbetalingslinjer -> Oppdrag(
                fagsystemId = get("personOppdrag.fagsystemId").asText(),
                utbetalingslinjer = utbetalingslinjer
            )}

            val fødselsnummer = get("fødselsnummer").asText()
            val gjenståendeSykedager = get("gjenståendeSykedager").asInt()

            return Utbetaling(
                fødselsnummer = fødselsnummer,
                gjenståendeSykedager = gjenståendeSykedager,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag
            )
        }
    }
}
