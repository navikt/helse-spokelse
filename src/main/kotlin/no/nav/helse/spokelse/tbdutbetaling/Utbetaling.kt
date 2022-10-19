package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje.Companion.utbetalingslinjerOrNull
import java.time.LocalDateTime
import java.util.UUID

internal data class Oppdrag(
    internal val fagsystemId: String,
    internal val utbetalingslinjer: List<Utbetalingslinje>
) {
    init {
        require(utbetalingslinjer.isNotEmpty()) {
            "Må være minst en utbetalingslinje for å lage ett oppdrag"
        }
    }
}

internal data class Utbetaling(
    internal val fødselsnummer: String,
    internal val korrelasjonsId: UUID,
    internal val gjenståendeSykedager: Int,
    internal val arbeidsgiverOppdrag: Oppdrag?,
    internal val personOppdrag: Oppdrag?,
    internal val sistUtbetalt: LocalDateTime
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
            val korrelasjonsId = UUID.fromString(get("korrelasjonsId").asText())
            val gjenståendeSykedager = get("gjenståendeSykedager").asInt()

            return Utbetaling(
                fødselsnummer = fødselsnummer,
                korrelasjonsId = korrelasjonsId,
                gjenståendeSykedager = gjenståendeSykedager,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                sistUtbetalt = LocalDateTime.now()
            )
        }
    }
}
