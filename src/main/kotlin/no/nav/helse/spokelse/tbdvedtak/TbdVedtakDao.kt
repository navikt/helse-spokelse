package no.nav.helse.spokelse.tbdvedtak

import kotliquery.Session
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.list
import no.nav.helse.spokelse.felles.update
import tools.jackson.databind.JsonNode

internal class TbdVedtakDao(
    private val session: Session,
) {
    // Samme vedtak kan leses flere ganger fra topicen, og lagres bare én gang.
    // Hele meldingen lagres, fordi topicen bare har 365 dagers historikk.
    internal fun lagre(
        vedtak: TbdVedtak,
        melding: JsonNode,
    ) {
        asSQL(
            """
            INSERT INTO tbd_vedtak(fødselsnummer, organisasjonsnummer, fom, tom, skjæringstidspunkt, vedtak_fattet_tidspunkt, melding)
            VALUES (:fodselsnummer, :organisasjonsnummer, :fom, :tom, :skjaeringstidspunkt, :vedtakFattetTidspunkt, :melding::jsonb)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            "fodselsnummer" to vedtak.fødselsnummer,
            "organisasjonsnummer" to vedtak.organisasjonsnummer,
            "fom" to vedtak.fom,
            "tom" to vedtak.tom,
            "skjaeringstidspunkt" to vedtak.skjæringstidspunkt,
            "vedtakFattetTidspunkt" to vedtak.vedtakFattetTidspunkt,
            "melding" to melding.toString(),
        ).update(session)
    }

    internal fun hent(fødselsnummer: String): List<TbdVedtak> =
        asSQL(
            """
            SELECT fødselsnummer, organisasjonsnummer, fom, tom, skjæringstidspunkt, vedtak_fattet_tidspunkt
            FROM tbd_vedtak
            WHERE fødselsnummer = :fodselsnummer
            """.trimIndent(),
            "fodselsnummer" to fødselsnummer,
        ).list(session) { row ->
            TbdVedtak(
                fødselsnummer = row.string("fødselsnummer"),
                organisasjonsnummer = row.stringOrNull("organisasjonsnummer"),
                fom = row.localDate("fom"),
                tom = row.localDate("tom"),
                skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
                vedtakFattetTidspunkt = row.instant("vedtak_fattet_tidspunkt"),
            )
        }
}
