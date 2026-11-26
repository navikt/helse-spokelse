package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.januar
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class TbdUtbetalingslinjeBackfillTest : AbstractE2ETest() {
    private val fødselsnummer = lagIdentitetsnummer()

    @BeforeEach
    fun nullstillFremdrift() {
        sql("UPDATE tbd_utbetalingslinje_backfill SET siste_id = 0, ferdig = false")
    }

    @Test
    fun `fyller dagsats og totalbeløp fra den lagrede meldingen, i bolker`() {
        lagreUtbetaling(
            arbeidsgiverOppdrag = Oppdrag("ARBEID", listOf(Utbetalingslinje(1.januar, 15.januar, 100.0, 1000, 11000), Utbetalingslinje(16.januar, 31.januar, 50.0, 500, 6000))),
            personOppdrag = Oppdrag("PERSON", listOf(Utbetalingslinje(16.januar, 31.januar, 50.0, 300, 3600))),
        )
        lagreUtbetaling(arbeidsgiverOppdrag = Oppdrag("ARBEID2", listOf(Utbetalingslinje(1.februar, 28.februar, 100.0, 1200, 24000))))
        sql("UPDATE tbdUtbetaling_Utbetalingslinje SET dagsats = NULL, totalbelop = NULL")

        kjørBackfill(bolkstørrelse = 1)

        assertEquals(
            setOf(
                Triple("ARBEID", 1.januar, 1000 to 11000),
                Triple("ARBEID", 16.januar, 500 to 6000),
                Triple("PERSON", 16.januar, 300 to 3600),
                Triple("ARBEID2", 1.februar, 1200 to 24000),
            ),
            linjer(),
        )
    }

    @Test
    fun `endrer ikke linjer som allerede har verdiene, og kan kjøres flere ganger`() {
        lagreUtbetaling(arbeidsgiverOppdrag = Oppdrag("ARBEID", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0, 1000, 23000))))
        sql("UPDATE tbdUtbetaling_Utbetalingslinje SET dagsats = 999, totalbelop = 9999")

        kjørBackfill()
        sql("UPDATE tbd_utbetalingslinje_backfill SET siste_id = 0, ferdig = false")
        kjørBackfill()

        assertEquals(setOf(Triple("ARBEID", 1.januar, 999 to 9999)), linjer())
    }

    @Test
    fun `hopper over bolken når en annen pod holder på`() {
        lagreUtbetaling(arbeidsgiverOppdrag = Oppdrag("ARBEID", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0, 1000, 23000))))
        sql("UPDATE tbdUtbetaling_Utbetalingslinje SET dagsats = NULL, totalbelop = NULL")

        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx.run(queryOf("SELECT 1 FROM tbd_utbetalingslinje_backfill FOR UPDATE").asExecute)
                assertEquals(TbdUtbetalingslinjeBackfill.Steg.OPPTATT, TbdUtbetalingslinjeBackfill({ dataSource }).nesteBolk())
            }
        }
        assertEquals(setOf(Triple("ARBEID", 1.januar, null to null)), linjer())
    }

    private fun lagreUtbetaling(
        arbeidsgiverOppdrag: Oppdrag? = null,
        personOppdrag: Oppdrag? = null,
    ) = tbdUtbetalingHåndterer.håndter(
        utbetalingUtbetalt(fødselsnummer = fødselsnummer, arbeidsgiverOppdrag = arbeidsgiverOppdrag, personOppdrag = personOppdrag),
        LocalDateTime.now(),
    )

    private fun kjørBackfill(bolkstørrelse: Int = 5_000) {
        val backfill = TbdUtbetalingslinjeBackfill({ dataSource }, bolkstørrelse)
        repeat(100) { if (backfill.nesteBolk() == TbdUtbetalingslinjeBackfill.Steg.FERDIG) return }
        error("Backfillen ble ikke ferdig")
    }

    private fun linjer() =
        sessionOf(dataSource)
            .use { session ->
                session.run(
                    queryOf("SELECT fagsystemId, fom, dagsats, totalbelop FROM tbdUtbetaling_Utbetalingslinje")
                        .map { Triple(it.string("fagsystemId"), it.localDate("fom"), it.intOrNull("dagsats") to it.intOrNull("totalbelop")) }
                        .asList,
                )
            }.toSet()

    private fun sql(statement: String) = sessionOf(dataSource).use { it.run(queryOf(statement).asUpdate) }
}
