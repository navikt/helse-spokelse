package no.nav.helse.spokelse

import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class UtbetalingsreferanseTest : AbstractE2ETest() {
    @Test
    fun `skriver dokumenter til hendelse`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"

        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)

        val refs =
            asSQL(
                "SELECT utbetalingsref FROM vedtak_utbetalingsref WHERE vedtaksperiode_id = :vedtaksperiodeId",
                "vedtaksperiodeId" to vedtaksperiodeId,
            ).list(dataSource) { row -> row.string("utbetalingsref") }

        assertEquals(1, refs.size)
        assertEquals(fagsystemId, refs.first())
    }
}
