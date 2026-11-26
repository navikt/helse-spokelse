package no.nav.helse.spokelse.tbdvedtak

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class TbdVedtak(
    val fødselsnummer: String,
    val organisasjonsnummer: String?,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val vedtakFattetTidspunkt: Instant,
) {
    internal companion object {
        private val OSLO = ZoneId.of("Europe/Oslo")

        // Organisasjonsnummer som ikke er 9 siffer (for eksempel SELVSTENDIG) blir null, som i utbetalingene fra tbd.utbetaling.
        internal fun JsonNode.vedtakFattetOrNull(): TbdVedtak? {
            if (path("vedtakFattetTidspunkt").isMissingOrNull()) return null
            return TbdVedtak(
                fødselsnummer = path("fødselsnummer").asString(),
                organisasjonsnummer = path("organisasjonsnummer").asString().takeIf { it.matches("\\d{9}".toRegex()) },
                fom = path("fom").asLocalDate(),
                tom = path("tom").asLocalDate(),
                skjæringstidspunkt = path("skjæringstidspunkt").asLocalDate(),
                vedtakFattetTidspunkt = path("vedtakFattetTidspunkt").asLocalDateTime().atZone(OSLO).toInstant(),
            )
        }

        // Skjæringstidspunktet fra det nyeste vedtaket hos arbeidsgiveren som dekker datoen.
        internal fun List<TbdVedtak>.skjæringstidspunkt(
            organisasjonsnummer: String?,
            dato: LocalDate,
        ) = filter { it.organisasjonsnummer == organisasjonsnummer && dato in it.fom..it.tom }
            .maxByOrNull { it.vedtakFattetTidspunkt }
            ?.skjæringstidspunkt
    }
}
