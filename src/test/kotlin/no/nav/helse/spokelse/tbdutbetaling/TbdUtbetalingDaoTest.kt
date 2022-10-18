package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class TbdUtbetalingDaoTest: AbstractE2ETest() {

    @Test
    fun `lagre utbetaling på førstegangsbehandling med full refusjon`() {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val korrelasjon = UUID.randomUUID()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
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
        val korrelasjon = UUID.randomUUID()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
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
        val korrelasjon = UUID.randomUUID()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
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

    @Test
    fun `oppdater utbetaling med delvis refusjon`() {
        // førstegangsbehandling med delvis refusjon
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val korrelasjon = UUID.randomUUID()
        val personutbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 33.7
            )
        )
        val arbeidsgiverutbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 50.7
            )
        )

        val førstegangsutbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = "person",
                utbetalingslinjer = personutbetalingslinjer
            ),
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "arbeid",
                utbetalingslinjer = arbeidsgiverutbetalingslinjer
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, førstegangsutbetaling)
        assertEquals(listOf(førstegangsutbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))

        // forlengelse med delvis refusjon
        val forlengelsesutbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = "person",
                utbetalingslinjer = personutbetalingslinjer + Utbetalingslinje(
                    fom = LocalDate.parse("2018-02-01"),
                    tom = LocalDate.parse("2018-02-28"),
                    grad = 51.0
                )
            ),
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "arbeid",
                utbetalingslinjer = arbeidsgiverutbetalingslinjer + Utbetalingslinje(
                    fom = LocalDate.parse("2018-02-01"),
                    tom = LocalDate.parse("2018-02-28"),
                    grad = 34.0
                )
            )
        )

        tbdUtbetalingDao.lagreUtbetaling(meldingId, forlengelsesutbetaling)
        assertEquals(listOf(forlengelsesutbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `Fra full refusjon til ingen refusjon`() {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val korrelasjon = UUID.randomUUID()
        val utbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 33.7
            )
        )
        val førstegangsutbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
            gjenståendeSykedager = 50,
            personOppdrag = null,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "arbeid",
                utbetalingslinjer = utbetalingslinjer
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, førstegangsutbetaling)

        val revurderingsutbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = "person",
                utbetalingslinjer = utbetalingslinjer
            ),
            arbeidsgiverOppdrag = null
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, revurderingsutbetaling)
        assertEquals(listOf(revurderingsutbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `Fra ingen refusjon til full refusjon`() {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}"))
        val fødselsnummer = "12345678910"
        val korrelasjon = UUID.randomUUID()
        val utbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 33.7
            )
        )
        val førstegangsutbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = "person",
                utbetalingslinjer = utbetalingslinjer
            ),
            arbeidsgiverOppdrag = null
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, førstegangsutbetaling)

        val revurderingsutbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjon,
            gjenståendeSykedager = 50,
            personOppdrag = null,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "arbeid",
                utbetalingslinjer = utbetalingslinjer
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, revurderingsutbetaling)
        assertEquals(listOf(revurderingsutbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `Legger til ferie i utbetalingen`() {

    }
}
