package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.gjenstaendesykedager.Vedtak
import no.nav.helse.spokelse.gjenstaendesykedager.Vedtak.Companion.vedtak
import no.nav.helse.spokelse.tbdutbetaling.Annullering.Companion.annullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erAnnullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetalingUtenUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.melding
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.utbetaling
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

internal sealed interface TbdUtbetalingMelding {
    val råmelding: Melding
    val korrelasjonsId: UUID

    data class Utbetaling(
        override val råmelding: Melding,
        val vedtak: Vedtak,
        val utbetaling: no.nav.helse.spokelse.tbdutbetaling.Utbetaling,
    ) : TbdUtbetalingMelding {
        override val korrelasjonsId get() = vedtak.korrelasjonsId
    }

    data class VedtakUtenUtbetaling(
        override val råmelding: Melding,
        val vedtak: Vedtak,
    ) : TbdUtbetalingMelding {
        override val korrelasjonsId get() = vedtak.korrelasjonsId
    }

    data class Annullering(
        override val råmelding: Melding,
        override val korrelasjonsId: UUID,
        val sendt: LocalDateTime,
        val annullering: no.nav.helse.spokelse.tbdutbetaling.Annullering,
    ) : TbdUtbetalingMelding

    companion object {
        // Gir null for eventer spøkelse ikke lagrer.
        internal fun JsonNode.tbdUtbetalingMelding(sendt: LocalDateTime): TbdUtbetalingMelding? =
            when {
                erUtbetaling -> Utbetaling(melding(sendt), vedtak(sendt), utbetaling(sendt))
                erUtbetalingUtenUtbetaling -> VedtakUtenUtbetaling(melding(sendt), vedtak(sendt))
                erAnnullering -> Annullering(melding(sendt), UUID.fromString(path("korrelasjonsId").asString()), sendt, annullering())
                else -> null
            }
    }
}
