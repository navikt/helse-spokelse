package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.*
import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.*
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
        val annuleringsMeldingId = nyMeldingId()
        tbdUtbetalingDao.annuller(annuleringsMeldingId, Annullering(ArbeidsgiverFagsystemId, null))
        assertEquals(annuleringsMeldingId, arbeidsgiverAnnuleringskilde(ArbeidsgiverFagsystemId))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person ved ingen refusjon`() {
        lagreNullRefusjon()
        val annuleringsMeldingId = nyMeldingId()
        tbdUtbetalingDao.annuller(annuleringsMeldingId, Annullering(null, PersonFagsystemId))
        assertEquals(annuleringsMeldingId, personAnnuleringskilde(PersonFagsystemId))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til arbeidsgiver ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        val annuleringsMeldingId = nyMeldingId()
        tbdUtbetalingDao.annuller(annuleringsMeldingId, Annullering(ArbeidsgiverFagsystemId, null))
        assertEquals(annuleringsMeldingId, arbeidsgiverAnnuleringskilde(ArbeidsgiverFagsystemId))
        assertEquals(listOf(utbetaling.copy(arbeidsgiverOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person ved delvis refusjon`() {
        val utbetaling = lagreDelvisRefusjon()
        val annuleringsMeldingId = nyMeldingId()
        tbdUtbetalingDao.annuller(annuleringsMeldingId, Annullering(null, PersonFagsystemId))
        assertEquals(annuleringsMeldingId, personAnnuleringskilde(PersonFagsystemId))
        assertEquals(listOf(utbetaling.copy(personOppdrag = null)), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
    }
    @Test
    fun `annullerer utbetaling til person og arbeidsgiver ved delvis refusjon`() {
        lagreDelvisRefusjon()
        val annuleringsMeldingId = nyMeldingId()
        tbdUtbetalingDao.annuller(annuleringsMeldingId, Annullering(ArbeidsgiverFagsystemId, PersonFagsystemId))
        assertEquals(annuleringsMeldingId, personAnnuleringskilde(PersonFagsystemId))
        assertEquals(annuleringsMeldingId, arbeidsgiverAnnuleringskilde(ArbeidsgiverFagsystemId))
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

    @Test
    fun `filtrerer på fom`() {
        val januar = lagreFullRefusjon(utbetalingslinjer = listOf(Utbetalingslinje(2.januar, 31.januar, 100.0)), fagsystemId = "1")
        val mars = lagreFullRefusjon(utbetalingslinjer = listOf(Utbetalingslinje(1.mars, 31.mars, 100.0)), fagsystemId = "2")
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 1.januar))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 2.januar))
        assertEquals(listOf(januar, mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 31.januar))
        assertEquals(listOf(mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 1.februar))
        assertEquals(listOf(mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 1.mars))
        assertEquals(listOf(mars), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 31.mars))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger(Fødselsnummer, 1.april))

        val april = lagreFullRefusjon(fødselsnummer = "22345678911", utbetalingslinjer = listOf(Utbetalingslinje(1.april, 1.april, 100.0)), fagsystemId = "3")
        assertEquals(listOf(april), tbdUtbetalingDao.hentUtbetalinger("22345678911", 31.mars))
        assertEquals(listOf(april), tbdUtbetalingDao.hentUtbetalinger("22345678911", 1.april))
        assertEquals(emptyList<Utbetaling>(), tbdUtbetalingDao.hentUtbetalinger("22345678911", 2.april))
    }

    private fun nyMeldingId() = tbdUtbetalingDao.lagreMelding(Melding("{}", nå(), "test_event", Fødselsnummer))
    private fun lagreFullRefusjon(
        korrelasjonsId: UUID = UUID.randomUUID(),
        fagsystemId: String = ArbeidsgiverFagsystemId,
        utbetalingslinjer: List<Utbetalingslinje> = listOf(
            Utbetalingslinje(fom = 1.januar, tom = 31.januar, grad = 50.7)
        ),
        fødselsnummer: String = Fødselsnummer
    ): Utbetaling {
        val meldingId = nyMeldingId()
        val utbetaling = Utbetaling(
            fødselsnummer = fødselsnummer,
            korrelasjonsId = korrelasjonsId,
            gjenståendeSykedager = 50,
            personOppdrag = null,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = fagsystemId,
                utbetalingslinjer = utbetalingslinjer
            ),
            sistUtbetalt = nå()
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
            arbeidsgiverOppdrag = null,
            sistUtbetalt = nå()
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
            ),
            sistUtbetalt = nå()
        )
        tbdUtbetalingDao.lagreUtbetaling(meldingId, utbetaling)
        return utbetaling
    }

    private fun personAnnuleringskilde(personFagsystemId: String) =
        "SELECT personAnnuleringskilde FROM tbdUtbetaling_Utbetaling WHERE personFagsystemId='$personFagsystemId'".let { sql ->
            sessionOf(dataSource).use { session ->
                session.run(queryOf(sql).map { it.long("personAnnuleringskilde") }.asList).singleOrNull()
            }
        }
    private fun arbeidsgiverAnnuleringskilde(arbeidsgiverFagsystemId: String) =
        "SELECT arbeidsgiverAnnuleringskilde FROM tbdUtbetaling_Utbetaling WHERE arbeidsgiverFagsystemId='$arbeidsgiverFagsystemId'".let { sql ->
            sessionOf(dataSource).use { session ->
                session.run(queryOf(sql).map { it.long("arbeidsgiverAnnuleringskilde") }.asList).singleOrNull()
            }
        }

    private companion object {
        private fun nå() = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDateTime()
        private const val Fødselsnummer = "12345678911"
        private const val ArbeidsgiverFagsystemId = "arbeid"
        private const val PersonFagsystemId = "person"
    }
}
