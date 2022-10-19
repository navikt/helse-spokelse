package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.time.LocalDateTime
import java.util.*

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
        private fun JsonNode.oppdrag(path:String) = path(path).takeUnless { it.isMissingOrNull() || it.path("utbetalingslinjer").isEmpty }?.let { Oppdrag(
            fagsystemId = it.path("fagsystemId").asText(),
            utbetalingslinjer = it.path("utbetalingslinjer").map { linje -> Utbetalingslinje(
                fom = linje.path("fom").asLocalDate(),
                tom = linje.path("tom").asLocalDate(),
                grad = linje.path("grad").asDouble()
            )}
        )}
        internal fun JsonNode.utbetaling(sistUtbetalt: LocalDateTime): Utbetaling {
            val arbeidsgiverOppdrag = oppdrag("arbeidsgiverOppdrag")
            val personOppdrag = oppdrag("personOppdrag")
            val fødselsnummer = get("fødselsnummer").asText()
            val korrelasjonsId = UUID.fromString(get("korrelasjonsId").asText())
            val gjenståendeSykedager = get("gjenståendeSykedager").asInt()
            return Utbetaling(
                fødselsnummer = fødselsnummer,
                korrelasjonsId = korrelasjonsId,
                gjenståendeSykedager = gjenståendeSykedager,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                sistUtbetalt = sistUtbetalt
            )
        }
    }
}
