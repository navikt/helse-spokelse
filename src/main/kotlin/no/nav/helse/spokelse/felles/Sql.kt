package no.nav.helse.spokelse.felles

import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal fun asSQL(
    @Language("PostgreSQL") sql: String,
    vararg params: Pair<String, Any?>,
) = queryOf(sql, params.toMap())

internal fun <T> Query.single(
    session: Session,
    mapping: (Row) -> T?,
) = session.run(map(mapping).asSingle)

internal fun <T> Query.single(
    dataSource: DataSource,
    mapping: (Row) -> T?,
) = sessionOf(dataSource).use { single(it, mapping) }

internal fun <T> Query.list(
    session: Session,
    mapping: (Row) -> T?,
) = session.run(map(mapping).asList)

internal fun <T> Query.list(
    dataSource: DataSource,
    mapping: (Row) -> T?,
) = sessionOf(dataSource).use { list(it, mapping) }

internal fun Query.update(session: Session) = session.run(asUpdate)
