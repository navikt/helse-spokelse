package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class TbdUtbetalingDaoTest: AbstractE2ETest() {

    @Test
    fun `lagre utbetaling på førstegangsbehandling med full refusjon`() {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            gjenståendeSykedager = 50,
            personOppdrag = null,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "arbeid",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        fom = LocalDate.parse("2018-01-01"),
                        tom = LocalDate.parse("2018-01-31"),
                        grad = 50.7
                    )
                )
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med null refusjon`() {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = "person",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        fom = LocalDate.parse("2018-01-01"),
                        tom = LocalDate.parse("2018-01-31"),
                        grad = 50.7
                    )
                )
            ),
            arbeidsgiverOppdrag = null
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med delvis refusjon`() {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = "person",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        fom = LocalDate.parse("2018-01-01"),
                        tom = LocalDate.parse("2018-01-31"),
                        grad = 33.7
                    )
                )
            ),
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "arbeid",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        fom = LocalDate.parse("2018-01-01"),
                        tom = LocalDate.parse("2018-01-31"),
                        grad = 50.7
                    )
                )
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }
}
