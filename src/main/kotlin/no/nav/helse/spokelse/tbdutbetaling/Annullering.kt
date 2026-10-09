package no.nav.helse.spokelse.tbdutbetaling

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erAnnullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.fødselsnummer
import tools.jackson.databind.JsonNode
import java.time.LocalDate

internal data class Annullering(
    internal val fødselsnummer: String,
    internal val fom: LocalDate,
    internal val arbeidsgiverFagsystemId: String?,
    internal val personFagsystemId: String?,
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
                fødselsnummer = fødselsnummer,
                fom = path("fom").asLocalDate(),
                arbeidsgiverFagsystemId = path("arbeidsgiverFagsystemId").takeUnless { it.isMissingOrNull() }?.asText(),
                personFagsystemId = path("personFagsystemId").takeUnless { it.isMissingOrNull() }?.asText(),
            )
        }
    }
}
