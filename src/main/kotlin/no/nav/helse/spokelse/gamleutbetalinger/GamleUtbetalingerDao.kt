package no.nav.helse.spokelse.gamleutbetalinger

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingObserver
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.time.measureTimedValue

internal class GamleUtbetalingerDao(private val dataSource: () -> DataSource): TbdUtbetalingObserver {

    internal companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        private val harDataFraOgMed = LocalDate.parse("2019-10-25")
        private val harDataTilOgMed = LocalDate.parse("2022-03-16")
        private fun etÅrFremFraNå() = LocalDate.now().plusYears(1)
        internal fun harData(fraOgMed: LocalDate?) = fraOgMed == null || fraOgMed <= harDataTilOgMed
    }

    internal fun hentUtbetalinger(fødselsnummer: String, fom: LocalDate?): List<GammelUtbetaling> {
        val benyttetFom = fom ?: harDataFraOgMed
        val benyttetTom = maxOf(benyttetFom, etÅrFremFraNå())
        return hentUtbetalinger(fødselsnummer, benyttetFom, benyttetTom)
    }
    internal fun hentUtbetalinger(fødselsnummer: String, fom: LocalDate, tom: LocalDate): List<GammelUtbetaling> {
        if (!harData(fom)) return emptyList()
        return hentFraDb(fødselsnummer, fom, tom)
    }

    private fun hentFraDb(fødselsnummer: String, fom: LocalDate, tom: LocalDate): List<GammelUtbetaling> {
        val (perioder, duration) = measureTimedValue {
            @Language("PostgreSQL")
            val vedtakOppdragOgUtbetalingQuery = """
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
            """

            @Language("PostgreSQL")
            val oldVedtakOgOldUtbetalingQuery = """
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
            """

            @Language("PostgreSQL")
            val gamleUtbetalingerQuery = """
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
            """

            @Language("PostgreSQL")
            val sammenstiltQuery = """
                with alle_gamle_utbetalinger as ($vedtakOppdragOgUtbetalingQuery UNION ALL $oldVedtakOgOldUtbetalingQuery UNION ALL $gamleUtbetalingerQuery)
                SELECT * FROM alle_gamle_utbetalinger agu LEFT JOIN alle_annulleringer aa ON agu.fagsystem_id = aa.fagsystem_id WHERE aa.fagsystem_id is null
            """

            sessionOf(dataSource()).use { session ->
                session.run(queryOf(sammenstiltQuery, mapOf("fodselsnummer" to fødselsnummer, "fom" to fom, "tom" to tom)).map { row ->
                    GammelUtbetaling(
                        fødselsnummer = fødselsnummer,
                        fagsystemId = row.string("fagsystem_id"),
                        organisasjonsnummer = row.string("organisasjonsnummer"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        grad = row.int("grad"),
                        kilde = row.string("kilde"),
                        utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt")
                    )
                }.asList)
            }
        }
        sikkerLogg.info("Oppslag mot gamle utbetalinger tok ${duration.inWholeMilliseconds}ms")
        return perioder
    }

    override fun utbetaling(meldingId: Long, utbetaling: Utbetaling) {
        // Nye utbetalinger er ikke aktuelt å lagre, det er kun annulleringer av gamle utbetalinger som er viktig å få med seg.
    }

    override fun annullering(meldingId: Long, annullering: Annullering) {
        @Language("PostgreSQL")
        val sql = "INSERT INTO alle_annulleringer (fagsystem_id) VALUES(:fagsystem_id) ON CONFLICT DO NOTHING"

        sessionOf(dataSource()).use { session ->
            listOfNotNull(annullering.arbeidsgiverFagsystemId, annullering.personFagsystemId).forEach { fagsystemId ->
                sikkerLogg.info("Lagrer annullering av fagsystemId $fagsystemId i tabellen alle_annulleringer")
                session.run(queryOf(sql, mapOf("fagsystem_id" to fagsystemId)).asExecute)
            }
        }
    }
}
