package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import java.time.LocalDate

internal data class Utbetalingslinje(
    internal val fom: LocalDate,
    internal val tom: LocalDate,
    internal val grad: Double
) {
    internal companion object {
        internal fun JsonNode.utbetalingslinje() = Utbetalingslinje(
            fom = path("fom").asLocalDate(),
            tom = path("tom").asLocalDate(),
            grad = path("grad").asDouble()
        )
    }
}
