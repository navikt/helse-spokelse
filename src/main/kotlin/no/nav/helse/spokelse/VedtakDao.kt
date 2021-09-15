package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class VedtakDao(private val dataSource: DataSource) {
    fun save(vedtak: OldVedtak) {
        sessionOf(dataSource, true).use {
            it.transaction { session ->
                @Language("PostgreSQL")
                val query = """INSERT INTO old_vedtak(
                vedtaksperiode_id,
                fodselsnummer,
                orgnummer,
                opprettet,
                forbrukte_sykedager,
                gjenstående_sykedager,
                sykmelding_id,
                soknad_id,
                inntektsmelding_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
                val vedtakId = session.run(
                    queryOf(
                        query,
                        vedtak.vedtaksperiodeId,
                        vedtak.fødselsnummer,
                        vedtak.orgnummer,
                        vedtak.opprettet,
                        vedtak.forbrukteSykedager,
                        vedtak.gjenståendeSykedager,
                        vedtak.dokumenter.sykmelding.dokumentId,
                        vedtak.dokumenter.søknad.dokumentId,
                        vedtak.dokumenter.inntektsmelding?.dokumentId
                    ).asUpdateAndReturnGeneratedKey
                )

                @Language("PostgreSQL")
                val queryUtbetaling = "INSERT INTO old_utbetaling(vedtak_id, fom, tom, grad, dagsats, belop, totalbelop) VALUES (?, ?, ?, ?, ?, ?, ?)"
                vedtak.utbetalinger.forEach { utbetaling ->
                    session.run(
                        queryOf(
                            queryUtbetaling,
                            vedtakId,
                            utbetaling.fom,
                            utbetaling.tom,
                            utbetaling.grad,
                            utbetaling.dagsats,
                            utbetaling.beløp,
                            utbetaling.totalbeløp
                        ).asUpdate
                    )
                }
            }
        }
    }

    fun hentVedtakListe(fødselsnummer: String) = sessionOf(dataSource).use { session ->
        data class VedtakRow(
            val fagsystemId: String,
            val utbetaltTidspunkt: LocalDateTime,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Double
        )

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

    data class UtbetalingRad(
        val fagsystemId: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double,
        val gjenståendeSykedager: Int,
        val utbetaltTidspunkt: LocalDateTime,
        val refusjonstype: Refusjonstype
    )

    data class UtbetalingDTO(
        val fødselsnummer: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double,
        val gjenståendeSykedager: Int,
        val utbetaltTidspunkt: LocalDateTime,
        val refusjonstype: Refusjonstype
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

        return sessionOf(dataSource).use { session ->
            session.run(queryOf(spørring, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                UtbetalingRad(
                    fagsystemId = row.string("fagsystem_id"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom"),
                    grad = row.double("grad"),
                    gjenståendeSykedager = row.int("gjenstaende_sykedager"),
                    utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt"),
                    refusjonstype = Refusjonstype.fraFagområde(row.string("fagomrade"))
                )
            }.asList)
        }
    }

    fun hentAnnuleringerForFødselsnummer(fødselsnummer: String): List<String> {
        @Language("PostgreSQL")
        val spørring = "SELECT fagsystem_id FROM annullering WHERE fodselsnummer = :fodselsnummer"

        return sessionOf(dataSource).use { session ->
            session.run(queryOf(spørring, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                row.string("fagsystem_id")
            }.asList)
        }
    }
}
