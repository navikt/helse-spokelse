package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import java.util.*
import javax.sql.DataSource

class VedtakDAO(private val dataSource: DataSource) {
    fun save(vedtak: Vedtak) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO vedtak(id, fnr, vedtaksperiodeId, fom, tom, grad) VALUES (?, ?, ?, ?, ? ,?)",
                    (Math.random() * 1000).toInt(), //FIXME: La stå!!
                    vedtak.fnr,
                    vedtak.vedtaksperiodeId,
                    vedtak.fom,
                    vedtak.tom,
                    vedtak.grad
                ).asUpdate
            )
        }
    }

    fun hentVedtak(fnr: String) = sessionOf(dataSource)
        .use { session ->
            session.run(
                queryOf(
                    "SELECT vedtaksperiodeId, fom, tom, grad FROM vedtak WHERE fnr = ?",
                    fnr
                ).map { row ->
                    Vedtak(
                        fnr = fnr,
                        vedtaksperiodeId = UUID.fromString(row.string("vedtaksperiodeId")),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        grad =row.double("grad")
                    )
                }.asSingle
            )
        }
}
