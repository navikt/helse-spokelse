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
}
