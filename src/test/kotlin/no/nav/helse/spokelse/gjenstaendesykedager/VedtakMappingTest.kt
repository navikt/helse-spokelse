package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.gjenstaendesykedager.Vedtak.Companion.vedtak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class VedtakMappingTest {
    @Test
    fun `mapper utbetaling til vedtak`() {
        val korrelasjonsId = UUID.randomUUID()
        val sendt = LocalDateTime.parse("2023-02-01T12:00:00")
        val json =
            objectMapper.readTree(
                """
                {
                  "event": "utbetaling_utbetalt",
                  "korrelasjonsId": "$korrelasjonsId",
                  "fødselsnummer": "12345678911",
                  "fom": "2023-01-01",
                  "tom": "2023-01-31",
                  "gjenståendeSykedager": 221
                }
                """,
            )
        assertEquals(
            Vedtak(korrelasjonsId, "12345678911", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-01-31"), 221, sendt),
            json.vedtak(sendt),
        )
    }

    @Test
    fun `mapper vedtak uten utbetaling til vedtak`() {
        val korrelasjonsId = UUID.randomUUID()
        val sendt = LocalDateTime.parse("2023-02-01T12:00:00")
        val json =
            objectMapper.readTree(
                """
                {
                  "event": "utbetaling_uten_utbetaling",
                  "korrelasjonsId": "$korrelasjonsId",
                  "fødselsnummer": "12345678911",
                  "fom": "2023-01-01",
                  "tom": "2023-01-31",
                  "gjenståendeSykedager": 0
                }
                """,
            )
        assertEquals(
            Vedtak(korrelasjonsId, "12345678911", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-01-31"), 0, sendt),
            json.vedtak(sendt),
        )
    }

    @Test
    fun `annullering kan ikke mappes til vedtak`() {
        val json = objectMapper.readTree("""{"event": "utbetaling_annullert", "fødselsnummer": "12345678911"}""")
        assertThrows<IllegalArgumentException> { json.vedtak(LocalDateTime.now()) }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
