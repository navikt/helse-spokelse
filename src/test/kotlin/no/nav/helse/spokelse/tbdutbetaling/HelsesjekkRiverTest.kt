package no.nav.helse.spokelse.tbdutbetaling

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.Miljø
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HelsesjekkRiverTest : AbstractE2ETest() {
    @Test
    fun `varsler ved halv_time klokka halv ni i prod`() {
        val rapid = rapid(Miljø.PROD)
        rapid.sendTestMessage(halvTime())
        assertEquals(1, rapid.inspektør.size)
        assertEquals(
            "slackmelding",
            rapid.inspektør
                .message(0)
                .path("@event_name")
                .asString(),
        )
    }

    @Test
    fun `varsler ikke ved halv_time i dev`() {
        val rapid = rapid(Miljø.DEV)
        rapid.sendTestMessage(halvTime())
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `kan fortsatt starte helsesjekken manuelt i dev`() {
        val rapid = rapid(Miljø.DEV)
        rapid.sendTestMessage(manuellHelsesjekk())
        assertEquals(1, rapid.inspektør.size)
    }

    private fun rapid(miljø: Miljø) = TestRapid().also { HelsesjekkRiver(it, tbdUtbetalingDao, miljø) }

    private fun halvTime() = """{"@event_name": "halv_time", "@opprettet": "2026-11-26T08:30:00", "ukedag": "THURSDAY", "system_participating_services": []}"""

    private fun manuellHelsesjekk() = """{"@event_name": "spokelse_helsesjekk", "@opprettet": "2026-11-26T10:12:00", "system_participating_services": []}"""
}
