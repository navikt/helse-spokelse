package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class VedtakDao(val datasource: DataSource) {
    fun finn(hendelseIder: List<UUID>) = sessionOf(datasource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM hendelse WHERE hendelse_id = ANY((?)::uuid[])"
        session.run(
            queryOf(query, hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() })
                .map { row ->
                    Hendelse(
                        dokumentId = UUID.fromString(row.string("dokument_id")),
                        hendelseId = UUID.fromString(row.string("hendelse_id")),
                        type = enumValueOf(row.string("type"))
                    )
                }.asList
        )
    }
}
