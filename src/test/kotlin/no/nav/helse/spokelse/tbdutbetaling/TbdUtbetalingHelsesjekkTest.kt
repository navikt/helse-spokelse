package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.HOURS

internal class TbdUtbetalingHelsesjekkTest : AbstractE2ETest() {
    @Test
    fun `teller utbetalinger og annulleringer innenfor tidsrommet`() {
        val nå = LocalDateTime.now()
        utbetal(sendt = nå.minusHours(1), arbeidsgiverFagsystemId = "arbeid1")
        utbetal(sendt = nå.minusHours(1), arbeidsgiverFagsystemId = "arbeid2", personFagsystemId = "person1")
        utbetal(sendt = nå.minusHours(1), personFagsystemId = "person2")
        utbetal(sendt = nå.minusDays(3), arbeidsgiverFagsystemId = "gammel", personFagsystemId = "gammel")
        tbdUtbetalingHåndterer.håndter(utbetalingAnnullert(arbeidsgiverFagsystemId = "arbeid2", personFagsystemId = "person2"), nå)

        assertEquals(1, tbdUtbetalingDao.arbeidsgiverutbetalinger(2 to HOURS))
        assertEquals(1, tbdUtbetalingDao.arbeidsgiverannulleringer(2 to HOURS))
        assertEquals(1, tbdUtbetalingDao.personutbetalinger(2 to HOURS))
        assertEquals(1, tbdUtbetalingDao.personannulleringer(2 to HOURS))

        assertEquals(2, tbdUtbetalingDao.arbeidsgiverutbetalinger(4 to DAYS))
        assertEquals(2, tbdUtbetalingDao.personutbetalinger(4 to DAYS))
    }

    @Test
    fun `godtar ikke tidsrom under én enhet`() {
        assertThrows<IllegalStateException> { tbdUtbetalingDao.arbeidsgiverutbetalinger(0 to HOURS) }
    }

    private fun utbetal(
        sendt: LocalDateTime,
        arbeidsgiverFagsystemId: String? = null,
        personFagsystemId: String? = null,
    ) {
        val linjer = listOf(Utbetalingslinje(1.januar, 31.januar, 100.0))
        tbdUtbetalingHåndterer.håndter(
            utbetalingUtbetalt(
                arbeidsgiverOppdrag = arbeidsgiverFagsystemId?.let { Oppdrag(it, linjer) },
                personOppdrag = personFagsystemId?.let { Oppdrag(it, linjer) },
            ),
            sendt,
        )
    }
}
