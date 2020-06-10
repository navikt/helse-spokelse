package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
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
}
