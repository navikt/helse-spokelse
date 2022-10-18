package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.*

internal class TbdUtbetalingDaoTest: AbstractE2ETest() {

    @Test
    fun `lagre utbetaling på førstegangsbehandling med full refusjon`() {
        val utbetaling = lagreFullRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med null refusjon`() {
        val utbetaling = lagreNullRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `oppdater utbetaling med delvis refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        val personutbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = 1.januar,
                tom = 31.januar,
                grad = 33.7
            )
        )
        val arbeidsgiverutbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = 1.januar,
                tom = 31.januar,
                grad = 50.7
            )
        )

        val førstegangsutbetaling = lagreDelvisRefusjon(korrelasjonsId, arbeidsgiverutbetalingslinjer, personutbetalingslinjer)
        assertEquals(listOf(førstegangsutbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))

        val forlengelsesutbetaling = lagreDelvisRefusjon(
            korrelasjonsId = korrelasjonsId,
            arbeidsgiverUtbetalingslinjer = arbeidsgiverutbetalingslinjer + Utbetalingslinje(
                fom = 1.februar,
                tom = 28.februar,
                grad = 34.0
            ),
            personUtbetalingslinjer = personutbetalingslinjer + Utbetalingslinje(
                fom = 1.februar,
                tom = 28.februar,
                grad = 51.0
            )
        )
        assertEquals(listOf(forlengelsesutbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `Fra full refusjon til ingen refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreFullRefusjon(korrelasjonsId)
        val revurdertUtbetaling = lagreNullRefusjon(korrelasjonsId)
        assertEquals(listOf(revurdertUtbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `Fra ingen refusjon til full refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreNullRefusjon(korrelasjonsId)
        val revurdertUtbetaling = lagreFullRefusjon(korrelasjonsId)
        assertEquals(listOf(revurdertUtbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `Legger til ferie i utbetalingen`() {
        val korrelasjonsId = UUID.randomUUID()
        val utbetalingslinjer = listOf(Utbetalingslinje(
            fom = 1.januar,
            tom = 31.januar,
            grad = 50.7
        ))
        val førstegangsbehandlingUtbetaling = lagreNullRefusjon(korrelasjonsId, utbetalingslinjer)
        assertEquals(listOf(førstegangsbehandlingUtbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))

        val revurdertUtbetalingslinjer = listOf(
            Utbetalingslinje(
                fom = 1.januar,
                tom = 14.januar,
                grad = 50.7
            ),
            Utbetalingslinje(
                fom = 16.januar,
                tom = 31.januar,
                grad = 50.7
            )
        )
        val revurderingUtbetaling = lagreNullRefusjon(korrelasjonsId, revurdertUtbetalingslinjer)
        assertEquals(listOf(revurderingUtbetaling), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `annullerer utbetaling til arbeidsgiver ved full refusjon`() {
        lagreFullRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(ArbeidsgiverFagsystemId, null))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person ved ingen refusjon`() {
        lagreNullRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(null, PersonFagsystemId))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til arbeidsgiver ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(ArbeidsgiverFagsystemId, null))
        assertEquals(listOf(utbetaling.copy(arbeidsgiverOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(null, PersonFagsystemId))
        assertEquals(listOf(utbetaling.copy(personOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person og arbeidsgiver ved delvis refusjon`() {
        lagreDelvisRefusjon()
        tbdUtbetalingDao.annuller(nyMeldingId(), Annullering(ArbeidsgiverFagsystemId, PersonFagsystemId))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }

    @Test
    fun `flere utbetalinger på samme person`() {
        val januar = lagreNullRefusjon(
            fagsystemId = "januar",
            utbetalingslinjer = listOf(Utbetalingslinje(1.januar, 31.januar, 55.0))
        )
        val mars = lagreFullRefusjon(
            fagsystemId = "mars",
            utbetalingslinjer = listOf(Utbetalingslinje(1.mars, 31.mars, 44.3))
        )
        val mai = lagreDelvisRefusjon(
            personFagsystemId = "maiPerson",
            personUtbetalingslinjer = listOf(Utbetalingslinje(1.mai, 31.mai, 50.0)),
            arbeidsgiverFagsystemId = "maiArbeidsgiver",
            arbeidsgiverUtbetalingslinjer = listOf(Utbetalingslinje(1.mai, 31.mai, 50.0))
        )
        assertEquals(listOf(januar, mars, mai), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger("99999999999"))
    }

    private fun nyMeldingId() = tbdUtbetalingDao.lagreMelding(Melding("{}"))
    private fun lagreFullRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        fagsystemId: String = ArbeidsgiverFagsystemId,
        utbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7)
        )
    ): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = Fødselsnummer,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            personOppdrag = null,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = fagsystemId,
                utbetalingslinjer = utbetalingslinjer
            )
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        return utbetaling
    }
    private fun lagreNullRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        utbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 33.7)
        ),
        fagsystemId: String = PersonFagsystemId
    ): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = Fødselsnummer,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            personOppdrag = Oppdrag(
                fagsystemId = fagsystemId,
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
            Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7)
        ),
        personUtbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 33.7)
        ),
        arbeidsgiverFagsystemId: String = ArbeidsgiverFagsystemId,
        personFagsystemId: String = PersonFagsystemId,
    ): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = Fødselsnummer,
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
        private const val Fødselsnummer = "12345678911"
        private const val ArbeidsgiverFagsystemId = "arbeid"
        private const val PersonFagsystemId = "person"
        val Int.januar get() = LocalDate.of(2018, Month.JANUARY, this)
        val Int.februar get() = LocalDate.of(2018, Month.FEBRUARY, this)
        val Int.mars get() = LocalDate.of(2018, Month.MARCH, this)
        val Int.mai get() = LocalDate.of(2018, Month.MAY, this)
    }
}
