package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import java.time.LocalDate

internal data class Utbetalingslinje(
    internal val fom: LocalDate,
    internal val tom: LocalDate,
    internal val grad: Double
) {
    internal companion object {
        internal fun JsonMessage.utbetalingslinjerOrNull(path: String) =
            this[path].takeIf { it.isArray && !it.isEmpty }?.map {
                Utbetalingslinje(
                    fom = it.path("fom").asLocalDate(),
                    tom = it.path("tom").asLocalDate(),
                    grad = it.path("grad").asDouble()
                )
            }
    }
}
