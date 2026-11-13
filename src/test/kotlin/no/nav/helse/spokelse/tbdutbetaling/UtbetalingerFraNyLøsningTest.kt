package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.april
import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.single
import no.nav.helse.spokelse.januar
import no.nav.helse.spokelse.mai
import no.nav.helse.spokelse.mars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class UtbetalingerFraNyLøsningTest : AbstractE2ETest() {
    @Test
    fun `lagre utbetaling på førstegangsbehandling med full refusjon`() {
        val utbetaling = lagreFullRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med null refusjon`() {
        val utbetaling = lagreNullRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `lagre utbetaling på førstegangsbehandling med delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        assertEquals(listOf(utbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `oppdater utbetaling med delvis refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        val personutbetalingslinjer = listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 33.7))
        val arbeidsgiverutbetalingslinjer = listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7))

        val førstegangsutbetaling = lagreDelvisRefusjon(korrelasjonsId, arbeidsgiverutbetalingslinjer, personutbetalingslinjer)
        assertEquals(listOf(førstegangsutbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))

        val forlengelsesutbetaling =
            lagreDelvisRefusjon(
                korrelasjonsId = korrelasjonsId,
                arbeidsgiverUtbetalingslinjer = arbeidsgiverutbetalingslinjer + Utbetalingslinje(fom = 1.februar, tom = 28.februar, grad = 34.0),
                personUtbetalingslinjer = personutbetalingslinjer + Utbetalingslinje(fom = 1.februar, tom = 28.februar, grad = 51.0),
            )
        assertEquals(listOf(forlengelsesutbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `Fra full refusjon til ingen refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreFullRefusjon(korrelasjonsId)
        val revurdertUtbetaling = lagreNullRefusjon(korrelasjonsId)
        assertEquals(listOf(revurdertUtbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `Fra ingen refusjon til full refusjon`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreNullRefusjon(korrelasjonsId)
        val revurdertUtbetaling = lagreFullRefusjon(korrelasjonsId)
        assertEquals(listOf(revurdertUtbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `Legger til ferie i utbetalingen`() {
        val korrelasjonsId = UUID.randomUUID()
        val førstegangsbehandlingUtbetaling = lagreNullRefusjon(korrelasjonsId, listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7)))
        assertEquals(listOf(førstegangsbehandlingUtbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))

        val revurdertUtbetalingslinjer =
            listOf(
                Utbetalingslinje(fom = 1.januar, tom = 14.januar, grad = 50.7),
                Utbetalingslinje(fom = 16.januar, tom = 31.januar, grad = 50.7),
            )
        val revurderingUtbetaling = lagreNullRefusjon(korrelasjonsId, revurdertUtbetalingslinjer)
        assertEquals(listOf(revurderingUtbetaling), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `annullerer utbetaling til arbeidsgiver ved full refusjon`() {
        lagreFullRefusjon()
        annuller(arbeidsgiverFagsystemId = ARBEIDSGIVER_FAGSYSTEM_ID)
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `annullerer utbetaling til person ved ingen refusjon`() {
        lagreNullRefusjon()
        annuller(personFagsystemId = PERSON_FAGSYSTEM_ID)
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `annullerer utbetaling til arbeidsgiver ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        annuller(arbeidsgiverFagsystemId = ARBEIDSGIVER_FAGSYSTEM_ID)
        assertEquals(listOf(utbetaling.copy(arbeidsgiverOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `annullerer utbetaling til person ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        annuller(personFagsystemId = PERSON_FAGSYSTEM_ID)
        assertEquals(listOf(utbetaling.copy(personOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `annullerer utbetaling til person og arbeidsgiver ved delvis refusjon`() {
        lagreDelvisRefusjon()
        annuller(arbeidsgiverFagsystemId = ARBEIDSGIVER_FAGSYSTEM_ID, personFagsystemId = PERSON_FAGSYSTEM_ID)
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
    }

    @Test
    fun `annulleringen lagres med meldingen som kilde for riktig oppdrag`() {
        lagreDelvisRefusjon()

        annuller(arbeidsgiverFagsystemId = ARBEIDSGIVER_FAGSYSTEM_ID)
        val arbeidsgiverannullering = sisteMeldingId()
        assertEquals(arbeidsgiverannullering to null, annulleringskilder())

        annuller(personFagsystemId = PERSON_FAGSYSTEM_ID)
        assertEquals(arbeidsgiverannullering to sisteMeldingId(), annulleringskilder())
    }

    private fun sisteMeldingId() = asSQL("SELECT max(id) FROM tbdUtbetaling_Melding").single(dataSource) { it.long(1) }

    private fun annulleringskilder() =
        asSQL(
            "SELECT arbeidsgiverAnnuleringskilde, personAnnuleringskilde FROM tbdUtbetaling_Utbetaling WHERE arbeidsgiverFagsystemId = :arbeidsgiverFagsystemId",
            "arbeidsgiverFagsystemId" to ARBEIDSGIVER_FAGSYSTEM_ID,
        ).single(dataSource) { it.longOrNull("arbeidsgiverAnnuleringskilde") to it.longOrNull("personAnnuleringskilde") }

    @Test
    fun `flere utbetalinger på samme person`() {
        val januar = lagreNullRefusjon(fagsystemId = "januar", utbetalingslinjer = listOf(Utbetalingslinje(1.januar, 31.januar, 55.0)))
        val mars = lagreFullRefusjon(fagsystemId = "mars", utbetalingslinjer = listOf(Utbetalingslinje(1.mars, 31.mars, 44.3)))
        val mai =
            lagreDelvisRefusjon(
                personFagsystemId = "maiPerson",
                personUtbetalingslinjer = listOf(Utbetalingslinje(1.mai, 31.mai, 50.0)),
                arbeidsgiverFagsystemId = "maiArbeidsgiver",
                arbeidsgiverUtbetalingslinjer = listOf(Utbetalingslinje(1.mai, 31.mai, 50.0)),
            )
        assertEquals(listOf(januar, mars, mai), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger("99999999999"))
    }

    @Test
    fun `filtrerer på fom`() {
        val januar = lagreFullRefusjon(utbetalingslinjer = listOf(Utbetalingslinje(2.januar, 31.januar, 100.0)), fagsystemId = "1")
        val mars = lagreFullRefusjon(utbetalingslinjer = listOf(Utbetalingslinje(1.mars, 31.mars, 100.0)), fagsystemId = "2")
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 1.januar))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 2.januar))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 31.januar))
        assertEquals(listOf(mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 1.februar))
        assertEquals(listOf(mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 1.mars))
        assertEquals(listOf(mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 31.mars))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, 1.april))

        val april = lagreFullRefusjon(fødselsnummer = "22345678911", utbetalingslinjer = listOf(Utbetalingslinje(1.april, 1.april, 100.0)), fagsystemId = "3")
        assertEquals(listOf(april), tbdUtbetalingDao.hentUtbetalinger("22345678911", 31.mars))
        assertEquals(listOf(april), tbdUtbetalingDao.hentUtbetalinger("22345678911", 1.april))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger("22345678911", 2.april))
    }

    @Test
    fun `filtrerer på fom & tom`() {
        val januar = lagreFullRefusjon(utbetalingslinjer = listOf(Utbetalingslinje(2.januar, 31.januar, 100.0)), fagsystemId = "1")
        val mars = lagreFullRefusjon(utbetalingslinjer = listOf(Utbetalingslinje(1.mars, 31.mars, 100.0)), fagsystemId = "2")
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, fom = 1.januar, tom = 1.januar))
        assertEquals(listOf(januar), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, fom = 1.januar, tom = 28.februar))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, fom = 31.januar, tom = 1.mars))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(FØDSELSNUMMER, fom = 1.april, tom = 31.mai))
    }

    private var sendt = LocalDateTime.parse("2023-02-01T12:00:00")

    private fun nesteSendt() = sendt.also { sendt = sendt.plusMinutes(1) }

    private fun lagreFullRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        fagsystemId: String = ARBEIDSGIVER_FAGSYSTEM_ID,
        utbetalingslinjer: List<Utbetalingslinje> = listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7)),
        fødselsnummer: String = FØDSELSNUMMER,
    ) = lagre(korrelasjonsId, fødselsnummer, arbeidsgiverOppdrag = Oppdrag(fagsystemId, utbetalingslinjer), personOppdrag = null)

    private fun lagreNullRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        utbetalingslinjer: List<Utbetalingslinje> = listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 33.7)),
        fagsystemId: String = PERSON_FAGSYSTEM_ID,
    ) = lagre(korrelasjonsId, FØDSELSNUMMER, arbeidsgiverOppdrag = null, personOppdrag = Oppdrag(fagsystemId, utbetalingslinjer))

    private fun lagreDelvisRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        arbeidsgiverUtbetalingslinjer: List<Utbetalingslinje> = listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7)),
        personUtbetalingslinjer: List<Utbetalingslinje> = listOf(Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 33.7)),
        arbeidsgiverFagsystemId: String = ARBEIDSGIVER_FAGSYSTEM_ID,
        personFagsystemId: String = PERSON_FAGSYSTEM_ID,
    ) = lagre(
        korrelasjonsId,
        FØDSELSNUMMER,
        arbeidsgiverOppdrag = Oppdrag(arbeidsgiverFagsystemId, arbeidsgiverUtbetalingslinjer),
        personOppdrag = Oppdrag(personFagsystemId, personUtbetalingslinjer),
    )

    private fun lagre(
        korrelasjonsId: UUID,
        fødselsnummer: String,
        arbeidsgiverOppdrag: Oppdrag?,
        personOppdrag: Oppdrag?,
    ): Utbetaling {
        val sendt = nesteSendt()
        val melding =
            utbetalingUtbetalt(
                korrelasjonsId = korrelasjonsId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                gjenståendeSykedager = 50,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
            )
        tbdUtbetalingHåndterer.håndter(melding, sendt)
        return Utbetaling(
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = ORGANISASJONSNUMMER,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            personOppdrag = personOppdrag,
            sistUtbetalt = sendt,
        )
    }

    private fun annuller(
        arbeidsgiverFagsystemId: String? = null,
        personFagsystemId: String? = null,
    ) {
        tbdUtbetalingHåndterer.håndter(
            utbetalingAnnullert(fødselsnummer = FØDSELSNUMMER, arbeidsgiverFagsystemId = arbeidsgiverFagsystemId, personFagsystemId = personFagsystemId),
            nesteSendt(),
        )
    }

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private const val ORGANISASJONSNUMMER = "987654321"
        private const val ARBEIDSGIVER_FAGSYSTEM_ID = "arbeid"
        private const val PERSON_FAGSYSTEM_ID = "person"
    }
}
