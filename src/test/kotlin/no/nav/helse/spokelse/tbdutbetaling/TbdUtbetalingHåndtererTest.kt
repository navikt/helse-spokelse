package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.gjenstaendesykedager.SisteVedtak
import no.nav.helse.spokelse.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

internal class TbdUtbetalingHåndtererTest : AbstractE2ETest() {
    @Test
    fun `ingenting lagres og ingen bø sendes når en av de siste skrivingene feiler`() {
        val melding =
            utbetalingUtbetalt(
                arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0))),
            )

        medFeilVedLagringAvUtbetalingslinjer {
            assertThrows<PSQLException> { tbdUtbetalingHåndterer.håndter(melding, SENDT) }
        }

        assertEquals(0, antallRåmeldinger())
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
        assertNull(gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
        assertEquals(emptyList<Any>(), booProducer.history())
    }

    @Test
    fun `sier bø etter lagret utbetaling`() {
        val oppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0)))
        tbdUtbetalingHåndterer.håndter(utbetalingUtbetalt(arbeidsgiverOppdrag = oppdrag), SENDT)

        assertEquals(1, tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER).size)
        assertBø(FØDSELSNUMMER, 1.januar)
    }

    @Test
    fun `sier bø etter lagret annullering`() {
        tbdUtbetalingHåndterer.håndter(utbetalingAnnullert(arbeidsgiverFagsystemId = "arbeid", fom = 2.januar), SENDT)

        assertEquals(1, antallRåmeldinger())
        assertBø(FØDSELSNUMMER, 2.januar)
    }

    @Test
    fun `sier ikke bø for vedtak uten utbetaling`() {
        tbdUtbetalingHåndterer.håndter(utbetalingUtbetalt(event = "utbetaling_uten_utbetaling", gjenståendeSykedager = 0), SENDT)

        assertEquals(1, antallRåmeldinger())
        assertEquals(SisteVedtak(0, 31.januar), gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
        assertEquals(emptyList<Any>(), booProducer.history())
    }

    @Test
    fun `lagrer ingenting for ukjent event`() {
        tbdUtbetalingHåndterer.håndter(utbetalingUtbetalt(event = "noe_annet"), SENDT)

        assertEquals(0, antallRåmeldinger())
        assertEquals(emptyList<Any>(), booProducer.history())
    }

    @Test
    fun `lagrer utbetalingen selv om vedtaket mangler gyldig periode`() {
        val oppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0)))
        tbdUtbetalingHåndterer.håndter(utbetalingUtbetalt(arbeidsgiverOppdrag = oppdrag, fom = LocalDate.MIN), SENDT)

        assertNull(gjenståendeSykedagerDao.sisteVedtak(Personidentifikator(FØDSELSNUMMER)))
        assertEquals(1, tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER).size)
    }

    private fun assertBø(
        personidentifikator: String,
        fraOgMed: LocalDate,
    ) {
        val bø = booProducer.history().single()
        assertEquals("tbd.boo", bø.topic())
        val melding = objectMapper.readTree(bø.value())
        assertEquals(personidentifikator, melding.path("personidentifikator").asString())
        assertEquals("$fraOgMed", melding.path("fraOgMed").asString())
    }

    private fun medFeilVedLagringAvUtbetalingslinjer(block: () -> Unit) {
        sql(
            """
            CREATE FUNCTION feil_ved_lagring() RETURNS trigger AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'Feil ved lagring'; END; ${'$'}${'$'} LANGUAGE plpgsql;
            CREATE TRIGGER feil_ved_lagring BEFORE INSERT ON tbdutbetaling_utbetalingslinje FOR EACH ROW EXECUTE FUNCTION feil_ved_lagring();
            """,
        )
        try {
            block()
        } finally {
            sql(
                """
                DROP TRIGGER feil_ved_lagring ON tbdutbetaling_utbetalingslinje;
                DROP FUNCTION feil_ved_lagring();
                """,
            )
        }
    }

    private fun sql(statement: String) {
        sessionOf(dataSource).use { it.run(queryOf(statement).asExecute) }
    }

    private fun antallRåmeldinger() =
        sessionOf(dataSource).use { session ->
            session.run(queryOf("SELECT count(1) FROM tbdutbetaling_melding").map { it.int(1) }.asSingle)
        }

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private val SENDT = LocalDateTime.parse("2023-02-01T12:00:00")
        private val objectMapper = jacksonObjectMapper()
    }
}
