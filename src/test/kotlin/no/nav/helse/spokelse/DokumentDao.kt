package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class DokumentDao(val datasource: DataSource) {
    fun opprett(hendelse: Hendelse) {
        @Language("PostgreSQL")
        val query = "INSERT INTO hendelse(hendelse_id, dokument_id, type) VALUES(?,?,?) ON CONFLICT DO NOTHING"
        sessionOf(datasource).use { session ->
            session.run(
                queryOf(
                    query,
                    hendelse.hendelseId, hendelse.dokumentId, hendelse.type.name
                ).asUpdate
            )
        }
    }

    fun lagre(vedtaksperiodeId: UUID, fagsystemId: String) {
        @Language("PostgreSQL")
        val query = "INSERT INTO vedtak_utbetalingsref(vedtaksperiode_id, utbetalingsref) VALUES(?,?) ON CONFLICT DO NOTHING"
        sessionOf(datasource).use { session ->
            session.run(
                queryOf(
                    query,
                    vedtaksperiodeId,
                    fagsystemId
                ).asUpdate
            )
        }
    }
}
