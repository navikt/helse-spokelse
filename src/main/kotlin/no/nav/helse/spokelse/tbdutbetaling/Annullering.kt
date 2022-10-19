package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erAnnullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event

internal data class Annullering(
    internal val arbeidsgiverFagsystemId: String?,
    internal val personFagsystemId: String?
) {
    init {
        require(arbeidsgiverFagsystemId != null || personFagsystemId != null) {
            "Enten arbeidsgiverFagsystemId eller personFagsystemId må være satt "
        }
    }

    internal companion object {
        internal fun JsonNode.annullering(): Annullering {
            require(erAnnullering) { "Kan ikke mappe event $event til annullering" }
            return Annullering(
                arbeidsgiverFagsystemId = path("arbeidsgiverFagsystemId").takeUnless { it.isMissingOrNull() }?.asText(),
                personFagsystemId = path("personFagsystemId").takeUnless { it.isMissingOrNull() }?.asText()
            )
        }
    }
}
