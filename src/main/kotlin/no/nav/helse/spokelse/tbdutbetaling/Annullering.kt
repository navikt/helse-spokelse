package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.isMissingOrNull

internal class Annullering(
    internal val arbeidsgiverFagsystemId: String?,
    internal val personFagsystemId: String?
) {
    init {
        require(arbeidsgiverFagsystemId != null || personFagsystemId != null) {
            "Enten arbeidsgiverFagsystemId eller personFagsystemId må være satt "
        }
    }

    internal companion object {
        internal fun JsonNode.annullering() = Annullering(
            arbeidsgiverFagsystemId = path("arbeidsgiverFagsystemId").takeUnless { it.isMissingOrNull() }?.asText(),
            personFagsystemId = path("personFagsystemId").takeUnless { it.isMissingOrNull() }?.asText()
        )
    }
}
