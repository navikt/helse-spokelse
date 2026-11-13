package no.nav.helse.spokelse.gamleutbetalinger

import kotliquery.TransactionalSession
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.list
import no.nav.helse.spokelse.felles.update
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.time.measureTimedValue

internal class GamleUtbetalingerDao(
    private val dataSource: () -> DataSource,
) {
    internal companion object {
        private val harDataFraOgMed = LocalDate.parse("2019-10-25")
        private val harDataTilOgMed = LocalDate.parse("2022-03-16")

        internal fun harData(fraOgMed: LocalDate?) = fraOgMed == null || fraOgMed <= harDataTilOgMed
    }

    internal fun hentUtbetalinger(
        fødselsnummer: String,
        fom: LocalDate?,
    ): List<GammelUtbetaling> {
        if (!harData(fom)) return emptyList()
        return hentUtbetalinger(fødselsnummer, fom ?: harDataFraOgMed, harDataTilOgMed)
    }

    internal fun hentUtbetalinger(
        fødselsnummer: String,
        fom: LocalDate,
        tom: LocalDate,
    ): List<GammelUtbetaling> {
        if (!harData(fom)) return emptyList()
        return hentFraDb(fødselsnummer, fom, tom)
    }

    private fun hentFraDb(
        fødselsnummer: String,
        fom: LocalDate,
        tom: LocalDate,
    ): List<GammelUtbetaling> {
        val (perioder, duration) =
            measureTimedValue {
                asSQL(
                    """
                    WITH alle_gamle_utbetalinger AS (
                        SELECT o.fagsystemid fagsystem_id,
                            u.fom         fom,
                            u.tom         tom,
                            u.grad        grad,
                            v.opprettet   utbetalt_tidspunkt,
                            v.orgnummer   organisasjonsnummer,
                            'VedtakOppdragOgUtbetaling' kilde
                        FROM vedtak v
                        INNER JOIN oppdrag o on v.id = o.vedtak_id
                        INNER JOIN utbetaling u on o.id = u.oppdrag_id
                        WHERE v.fodselsnummer = :fodselsnummer
                        AND u.tom >= :fom AND NOT u.fom > :tom
                        UNION ALL
                        SELECT
                            (SELECT distinct vu.utbetalingsref FROM vedtak_utbetalingsref vu WHERE vu.vedtaksperiode_id = ov.vedtaksperiode_id) fagsystem_id,
                            ou.fom fom,
                            ou.tom tom,
                            ou.grad grad,
                            ov.opprettet utbetalt_tidspunkt,
                            ov.orgnummer organisasjonsnummer,
                            'OldVedtakOgOldUtbetaling' kilde
                        FROM old_vedtak ov
                        INNER JOIN old_utbetaling ou on ov.id = ou.vedtak_id
                        WHERE ov.fodselsnummer = :fodselsnummer
                        AND ou.tom >= :fom AND NOT ou.fom > :tom
                        UNION ALL
                        SELECT
                            fagsystem_id,
                            fom,
                            tom,
                            grad,
                            opprettet utbetalt_tidspunkt,
                            orgnummer organisasjonsnummer,
                            'GamleUtbetalinger' kilde
                        FROM gamle_utbetalinger
                        WHERE fodselsnummer = :fodselsnummer
                        AND tom >= :fom AND NOT fom > :tom
                    )
                    SELECT * FROM alle_gamle_utbetalinger agu
                    LEFT JOIN alle_annulleringer aa ON agu.fagsystem_id = aa.fagsystem_id
                    WHERE aa.fagsystem_id is null
                    """.trimIndent(),
                    "fodselsnummer" to fødselsnummer,
                    "fom" to fom,
                    "tom" to tom,
                ).list(dataSource()) { row ->
                    GammelUtbetaling(
                        fødselsnummer = fødselsnummer,
                        fagsystemId = row.string("fagsystem_id"),
                        organisasjonsnummer = row.string("organisasjonsnummer"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        grad = row.int("grad"),
                        kilde = row.string("kilde"),
                        utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt"),
                    )
                }
            }
        loggInfo("Oppslag mot gamle utbetalinger tok ${duration.inWholeMilliseconds}ms")
        return perioder
    }

    internal fun lagreAnnullering(
        transaksjon: TransactionalSession,
        annullering: Annullering,
    ) {
        listOfNotNull(annullering.arbeidsgiverFagsystemId, annullering.personFagsystemId).forEach { fagsystemId ->
            loggInfo("Lagrer annullering i tabellen alle_annulleringer", "fagsystemId" to fagsystemId)
            asSQL(
                """
                INSERT INTO alle_annulleringer (fagsystem_id)
                VALUES (:fagsystem_id)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                "fagsystem_id" to fagsystemId,
            ).update(transaksjon)
        }
    }
}
