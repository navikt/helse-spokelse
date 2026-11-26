package no.nav.helse.spokelse.tbdutbetaling

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import tools.jackson.databind.JsonNode
import java.time.LocalDate

internal data class Utbetalingslinje(
    internal val fom: LocalDate,
    internal val tom: LocalDate,
    internal val grad: Double,
    // Mangler for linjer som ble lagret før Spøkelse lagret dem, til backfillen har fylt dem inn.
    internal val dagsats: Int? = null,
    internal val totalbeløp: Int? = null,
) {
    internal companion object {
        internal fun JsonNode.utbetalingslinje() =
            Utbetalingslinje(
                fom = path("fom").asLocalDate(),
                tom = path("tom").asLocalDate(),
                grad = path("grad").asDouble(),
                dagsats = path("dagsats").takeUnless { it.isMissingOrNull() }?.asInt(),
                totalbeløp = path("totalbeløp").takeUnless { it.isMissingOrNull() }?.asInt(),
            )
    }
}
