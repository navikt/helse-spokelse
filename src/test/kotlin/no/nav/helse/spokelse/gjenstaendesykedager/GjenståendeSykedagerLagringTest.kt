package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.felles.Personidentifikator
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class GjenståendeSykedagerLagringTest : AbstractE2ETest() {
    @Test
    fun `nyeste vedtak per korrelasjonsId vinner`() {
        val korrelasjonsId = UUID.randomUUID()
        håndterVedtak(korrelasjonsId, gjenståendeSykedager = 221, sendt = 1.februar)
        håndterVedtak(korrelasjonsId, gjenståendeSykedager = 225, sendt = 1.mars)
        håndterVedtak(korrelasjonsId, gjenståendeSykedager = 200, sendt = 15.februar)

        assertEquals(SisteVedtak(225, TOM), gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
    }

    @Test
    fun `vedtak uten utbetaling lagres`() {
        håndterVedtak(UUID.randomUUID(), type = "utbetaling_uten_utbetaling", gjenståendeSykedager = 0, sendt = 1.mars)

        assertEquals(SisteVedtak(0, TOM), gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
    }

    @Test
    fun `annullert vedtak gir ikke treff`() {
        val korrelasjonsId = UUID.randomUUID()
        håndterVedtak(korrelasjonsId, gjenståendeSykedager = 221, sendt = 1.februar)
        håndterAnnullering(korrelasjonsId, sendt = 1.mars)

        assertNull(gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
    }

    @Test
    fun `ny utbetaling opphever annulleringen`() {
        val korrelasjonsId = UUID.randomUUID()
        håndterVedtak(korrelasjonsId, gjenståendeSykedager = 221, sendt = 1.februar)
        håndterAnnullering(korrelasjonsId, sendt = 15.februar)
        håndterVedtak(korrelasjonsId, gjenståendeSykedager = 210, sendt = 1.mars)

        assertEquals(SisteVedtak(210, TOM), gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
    }

    @Test
    fun `vedtak med LocalDate MIN lagres ikke`() {
        håndterVedtak(UUID.randomUUID(), gjenståendeSykedager = 221, sendt = 1.februar, fom = LocalDate.MIN)

        assertNull(gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
    }

    private fun håndterVedtak(
        korrelasjonsId: UUID,
        type: String = "utbetaling_utbetalt",
        gjenståendeSykedager: Int,
        sendt: LocalDateTime,
        fom: LocalDate = FOM,
    ) {
        @Language("JSON")
        val json =
            """
            {
              "event": "$type",
              "utbetalingId": "${UUID.randomUUID()}",
              "korrelasjonsId": "$korrelasjonsId",
              "fødselsnummer": "$FØDSELSNUMMER",
              "organisasjonsnummer": "999263550",
              "fom": "$fom",
              "tom": "$TOM",
              "gjenståendeSykedager": $gjenståendeSykedager,
              "arbeidsgiverOppdrag": {
                "fagsystemId": "${UUID.randomUUID()}",
                "utbetalingslinjer": [{ "fom": "$FOM", "tom": "$TOM", "grad": 100.0 }]
              },
              "personOppdrag": { "fagsystemId": "${UUID.randomUUID()}", "utbetalingslinjer": [] }
            }
            """
        tbdUtbetalingHåndterer.håndter(objectMapper.readTree(json), sendt)
    }

    private fun håndterAnnullering(
        korrelasjonsId: UUID,
        sendt: LocalDateTime,
    ) {
        @Language("JSON")
        val json =
            """
            {
              "event": "utbetaling_annullert",
              "utbetalingId": "${UUID.randomUUID()}",
              "korrelasjonsId": "$korrelasjonsId",
              "fødselsnummer": "$FØDSELSNUMMER",
              "organisasjonsnummer": "999263550",
              "fom": "$FOM",
              "tom": "$TOM",
              "arbeidsgiverFagsystemId": "${UUID.randomUUID()}",
              "personFagsystemId": null
            }
            """
        tbdUtbetalingHåndterer.håndter(objectMapper.readTree(json), sendt)
    }

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private val FOM = LocalDate.parse("2023-01-01")
        private val TOM = LocalDate.parse("2023-01-31")
        private val objectMapper = jacksonObjectMapper()
        private val Int.februar get() = LocalDateTime.of(2023, 2, this, 12, 0)
        private val Int.mars get() = LocalDateTime.of(2023, 3, this, 12, 0)
    }
}
