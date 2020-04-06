package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

class VedtakDao(private val dataSource: DataSource) {
    fun save(vedtak: Vedtak) {
        sessionOf(dataSource).use { session ->
            session.run(
                    queryOf(
                            "INSERT INTO vedtak(id) VALUES (?)",
                            vedtak.id
                    ).asUpdate
            )
        }
    }
}
