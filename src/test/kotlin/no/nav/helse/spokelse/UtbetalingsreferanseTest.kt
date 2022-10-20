package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class UtbetalingsreferanseTest : AbstractE2ETest() {

    @Test
    fun `skriver dokumenter til hendelse`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"

        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)

        val refs = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """SELECT utbetalingsref
            FROM vedtak_utbetalingsref
            WHERE vedtaksperiode_id = ?"""
            session.run(
                queryOf(
                    query,
                    vedtaksperiodeId
                ).map { row ->
                    row.string("utbetalingsref")
                }.asList
            )
        }

        assertEquals(1, refs.size)
        assertEquals(fagsystemId, refs.first())
    }
}
