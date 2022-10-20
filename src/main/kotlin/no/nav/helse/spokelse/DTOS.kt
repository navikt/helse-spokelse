package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.LocalDateTime

data class FpVedtak(
    val vedtaksreferanse: String,
    val utbetalinger: List<Utbetalingsperiode>,
    val vedtattTidspunkt: LocalDateTime
)

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)

data class UtbetalingDTO(
    val fødselsnummer: String,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val grad: Double,
    val gjenståendeSykedager: Int?,
    val utbetaltTidspunkt: LocalDateTime?,
    val refusjonstype: Refusjonstype?
)

enum class Refusjonstype(private val fagområde: String) {
    REFUSJON_TIL_ARBEIDSGIVER("SPREF"),
    REFUSJON_TIL_PERSON("SP");

    companion object {
        fun fraFagområde(fagområde: String): Refusjonstype {
            return values().single {
                it.fagområde == fagområde
            }
        }
    }
}
