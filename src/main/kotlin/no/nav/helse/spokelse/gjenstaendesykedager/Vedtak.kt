package no.nav.helse.spokelse.gjenstaendesykedager

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetalingUtenUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.fødselsnummer
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal data class Vedtak(
    val korrelasjonsId: UUID,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val gjenståendeSykedager: Int,
    val sendt: LocalDateTime,
) {
    internal companion object {
        internal fun JsonNode.vedtak(sendt: LocalDateTime): Vedtak {
            require(erUtbetaling || erUtbetalingUtenUtbetaling) { "Kan ikke mappe event $event til vedtak" }
            require(path("gjenståendeSykedager").isIntegralNumber) { "Mangler gjenståendeSykedager på $event" }
            return Vedtak(
                korrelasjonsId = UUID.fromString(path("korrelasjonsId").asText()),
                fødselsnummer = fødselsnummer,
                fom = path("fom").asLocalDate(),
                tom = path("tom").asLocalDate(),
                gjenståendeSykedager = path("gjenståendeSykedager").asInt(),
                sendt = sendt,
            )
        }
    }
}
