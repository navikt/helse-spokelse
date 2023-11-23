package no.nav.helse.spokelse.gamlevedtak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.FpVedtak
import no.nav.helse.spokelse.Refusjonstype
import no.nav.helse.spokelse.Utbetalingsperiode
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class HentVedtakDao(private val dataSource: () -> DataSource) {

    internal companion object {
        private val harDataTilOgMed = LocalDate.parse("2022-03-16")
        internal fun harData(fraOgMed: LocalDate?) = fraOgMed == null || fraOgMed <= harDataTilOgMed
        internal fun List<VedtakRow>.filtrer(fraOgMed: LocalDate?) = when (fraOgMed) {
            null -> this
            else -> filter { it.tom >= fraOgMed }
        }
    }

    data class VedtakRow(
        val fagsystemId: String,
        val utbetaltTidspunkt: LocalDateTime,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double
    )

    fun hentVedtakListe(fødselsnummer: String, fom: LocalDate?): List<FpVedtak> {
        if (!harData(fom)) return emptyList()
        return sessionOf(dataSource()).use { session ->
            @Language("PostgreSQL")
            val query = """
            (SELECT o.fagsystemid fagsystem_id,
                    u.fom         fom,
                    u.tom         tom,
                    u.grad        grad,
                    v.opprettet   utbetalt_tidspunkt
             FROM vedtak v
                      INNER JOIN oppdrag o on v.id = o.vedtak_id
                      INNER JOIN utbetaling u on o.id = u.oppdrag_id
             WHERE v.fodselsnummer = :fodselsnummer)
            UNION ALL
            (SELECT (SELECT distinct vu.utbetalingsref
                     FROM vedtak_utbetalingsref vu
                     WHERE vu.vedtaksperiode_id = ov.vedtaksperiode_id) fagsystem_id,
                    ou.fom                                              fom,
                    ou.tom                                              tom,
                    ou.grad                                             grad,
                    ov.opprettet                                        utbetalt_tidspunkt
             FROM old_vedtak ov
                      INNER JOIN old_utbetaling ou on ov.id = ou.vedtak_id
             WHERE ov.fodselsnummer = :fodselsnummer)
            ORDER BY utbetalt_tidspunkt, fom, tom
                """
            session.run(queryOf(query, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                VedtakRow(
                    fagsystemId = row.string("fagsystem_id"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom"),
                    grad = row.double("grad"),
                    utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt")
                )
            }.asList)
                .filtrer(fom)
                .groupBy { it.fagsystemId }
                .map { (_, value) ->
                    FpVedtak(
                        vedtaksreferanse = value.first().fagsystemId,
                        utbetalinger = value.map { utbetaling ->
                            Utbetalingsperiode(
                                fom = utbetaling.fom,
                                tom = utbetaling.tom,
                                grad = utbetaling.grad
                            )
                        },
                        vedtattTidspunkt = value.first().utbetaltTidspunkt
                    )
                }
        }
    }

    data class UtbetalingRad(
        val fagsystemId: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double,
        val gjenståendeSykedager: Int?,
        val utbetaltTidspunkt: LocalDateTime?,
        val refusjonstype: Refusjonstype
    )

    fun hentUtbetalingerForFødselsnummer(fødselsnummer: String): List<UtbetalingRad> {
        @Language("PostgreSQL")
        val spørring = """
        SELECT fagsystem_id,
               fom,
               tom,
               grad,
               gjenstaende_sykedager,
               opprettet utbetalt_tidspunkt,
               fagomrade
        FROM gamle_utbetalinger
        WHERE fodselsnummer = :fodselsnummer
        UNION ALL
        SELECT vo.fagsystemid           fagsystem_id,
               u.fom                    fom,
               u.tom                    tom,
               u.grad                   grad,
               vo.gjenstående_sykedager gjenstaende_sykedager,
               vo.opprettet             utbetalt_tidspunkt,
               vo.fagområde             fagomrade
        FROM (
                 SELECT DISTINCT ON (o.fagsystemid) o.fagsystemid,
                                                    v.gjenstående_sykedager,
                                                    v.opprettet,
                                                    o.id AS oppdrag_id,
                                                    o.fagområde
                 FROM vedtak v
                          INNER JOIN oppdrag o ON v.id = o.vedtak_id
                 WHERE v.fodselsnummer = :fodselsnummer
                 ORDER BY fagsystemid, opprettet DESC) AS vo
                 INNER JOIN utbetaling u ON vo.oppdrag_id = u.oppdrag_id
        UNION ALL
        SELECT utbetalingsref        fagsystem_id,
               fom                   fom,
               tom                   tom,
               grad                  grad,
               gjenstående_sykedager gjenstaende_sykedager,
               opprettet             utbetalt_tidspunkt,
               'SPREF'               fagomrade
        FROM (SELECT DISTINCT ON (utbetalingsref) utbetalingsref,
                                                  gjenstående_sykedager,
                                                  opprettet,
                                                  id
              FROM old_vedtak ov
                       INNER JOIN vedtak_utbetalingsref vu ON ov.vedtaksperiode_id = vu.vedtaksperiode_id
              WHERE fodselsnummer = :fodselsnummer
              ORDER BY utbetalingsref, opprettet DESC
             ) AS vo
                 INNER JOIN old_utbetaling ON vedtak_id = vo.id;
        """

        return sessionOf(dataSource()).use { session ->
            session.run(queryOf(spørring, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                UtbetalingRad(
                    fagsystemId = row.string("fagsystem_id"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom"),
                    grad = row.double("grad"),
                    gjenståendeSykedager = row.intOrNull("gjenstaende_sykedager"),
                    utbetaltTidspunkt = row.localDateTimeOrNull("utbetalt_tidspunkt"),
                    refusjonstype = Refusjonstype.fraFagområde(row.string("fagomrade"))
                )
            }.asList)
        }
    }

    fun hentAnnuleringerForFødselsnummer(fødselsnummer: String): List<String> {
        @Language("PostgreSQL")
        val spørring = "SELECT fagsystem_id FROM annullering WHERE fodselsnummer = :fodselsnummer"

        return sessionOf(dataSource()).use { session ->
            session.run(queryOf(spørring, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                row.string("fagsystem_id")
            }.asList)
        }
    }
}
