package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

class VedtakDAO(private val dataSource: DataSource) {
    fun save(vedtak: Vedtak) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO vedtak(id, fnr) VALUES (?, ?)",
                    vedtak.id,
                    vedtak.fnr
                ).asUpdate
            )
        }
    }

    fun hentVedtak(fnr: String) = sessionOf(dataSource)
        .use { session ->
            session.run(
                queryOf(
                    "SELECT id FROM vedtak WHERE fnr = ?",
                    fnr
                ).map { row ->
                    Vedtak(row.int("id"), fnr)
                }.asSingle
            )
        }
}
