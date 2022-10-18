package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class TbdUtbetalingDaoTest: AbstractE2ETest() {

    @Test
    fun `lagre utbetaling på førstegangsbehandling med full refusjon`() {
        val utbetaling = lagreFullRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med null refusjon`() {
        val utbetaling = lagreNullRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `oppdater utbetaling med delvis refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
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

        val førstegangsutbetaling = lagreDelvisRefusjon(korrelasjonsId, arbeidsgiverutbetalingslinjer, personutbetalingslinjer)
        assertEquals(listOf(førstegangsutbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))

        val forlengelsesutbetaling = lagreDelvisRefusjon(
            korrelasjonsId = korrelasjonsId,
            arbeidsgiverUtbetalingslinjer = arbeidsgiverutbetalingslinjer + Utbetalingslinje(
                fom = LocalDate.parse("2018-02-01"),
                tom = LocalDate.parse("2018-02-28"),
                grad = 34.0
            ),
            personUtbetalingslinjer = personutbetalingslinjer + Utbetalingslinje(
                fom = LocalDate.parse("2018-02-01"),
                tom = LocalDate.parse("2018-02-28"),
                grad = 51.0
            )
        )
        assertEquals(listOf(forlengelsesutbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `Fra full refusjon til ingen refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreFullRefusjon(korrelasjonsId)
        val revurdertUtbetaling = lagreNullRefusjon(korrelasjonsId)
        assertEquals(listOf(revurdertUtbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `Fra ingen refusjon til full refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreNullRefusjon(korrelasjonsId)
        val revurdertUtbetaling = lagreFullRefusjon(korrelasjonsId)
        assertEquals(listOf(revurdertUtbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `Legger til ferie i utbetalingen`() {
        val korrelasjonsId = UUID.randomUUID()
        val utbetalingslinjer = listOf(Utbetalingslinje(
            fom = LocalDate.parse("2018-01-01"),
            tom = LocalDate.parse("2018-01-31"),
            grad = 50.7
        ))
        val førstegangsbehandlingUtbetaling = lagreNullRefusjon(korrelasjonsId, utbetalingslinjer)
        assertEquals(listOf(førstegangsbehandlingUtbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))

        val revurdertUtbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-14"),
                grad = 50.7
            ),
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-16"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 50.7
            )
        )
        val revurderingUtbetaling = lagreNullRefusjon(korrelasjonsId, revurdertUtbetalingslinjer)
        assertEquals(listOf(revurderingUtbetaling), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    @Test
    fun `annullerer utbetaling til arbeidsgiver ved full refusjon`() {
        lagreFullRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(arbeidsgiverFagsystemId, null))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person ved ingen refusjon`() {
        lagreNullRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(null, personFagsystemId))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til arbeidsgiver ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(arbeidsgiverFagsystemId, null))
        assertEquals(listOf(utbetaling.copy(arbeidsgiverOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(null, personFagsystemId))
        assertEquals(listOf(utbetaling.copy(personOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person og arbeidsgiver ved delvis refusjon`() {
        lagreDelvisRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(arbeidsgiverFagsystemId, personFagsystemId))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(fødselsnummer))
    }

    private fun nyMeldingId() = tbdUtbetalingDao.lagreMelding(Melding("{}"))
    private fun lagreFullRefusjon(korrelasjonsId: UUID = UUID.randomUUID()): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            personOppdrag = null,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = arbeidsgiverFagsystemId,
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
        return utbetaling
    }
    private fun lagreNullRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        utbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 50.7
            )
        )): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = personFagsystemId,
                utbetalingslinjer = utbetalingslinjer
            ),
            arbeidsgiverOppdrag = null
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        return utbetaling
    }
    private fun lagreDelvisRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        arbeidsgiverUtbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 50.7
            )
        ),
        personUtbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(
                fom = LocalDate.parse("2018-01-01"),
                tom = LocalDate.parse("2018-01-31"),
                grad = 33.7
            )
        )): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = personFagsystemId,
                utbetalingslinjer = personUtbetalingslinjer
            ),
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = arbeidsgiverFagsystemId,
                utbetalingslinjer = arbeidsgiverUtbetalingslinjer
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        return utbetaling
    }

    private companion object {
        private const val fødselsnummer = "12345678911"
        private const val arbeidsgiverFagsystemId = "arbeid"
        private const val personFagsystemId = "person"
    }
}
