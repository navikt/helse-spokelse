package no.nav.helse.spokelse.utbetaleperioder

import no.nav.helse.spokelse.*
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somSpøkelsePerioder
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somUtbetalteLinjer
import no.nav.helse.spokelse.personoversikt.Mottaker.ARBEIDSGIVER
import no.nav.helse.spokelse.personoversikt.Mottaker.PERSON
import no.nav.helse.spokelse.personoversikt.SpeilV1Linje
import no.nav.helse.spokelse.personoversikt.SpeilV2Linje
import no.nav.helse.spokelse.personoversikt.SpeilV3Linje
import no.nav.helse.spokelse.tbdutbetaling.utbetalingAnnullert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

internal class SpøkelsePerioderFraGamleUtbetalingerTest : AbstractE2ETest() {
    @Test
    fun `hente utbetalte perioder fra gamle tabeller`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1, fødselsnummer)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2, fødselsnummer)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3, fødselsnummer)
        assertEquals(
            setOf(
                SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.januar, tom = 31.januar, grad = 100, organisasjonsnummer = organisasjonsnummer, setOf("Spleis", "OldVedtakOgOldUtbetaling")),
                SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.februar, tom = 28.februar, grad = 100, organisasjonsnummer = organisasjonsnummer, setOf("Spleis", "VedtakOppdragOgUtbetaling")),
                SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.mars, tom = 31.mars, grad = 100, organisasjonsnummer = organisasjonsnummer, setOf("Spleis", "GamleUtbetalinger")),
            ),
            gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.mars).somSpøkelsePerioder(),
        )
    }

    @Test
    fun `returnerer ikke utbetalinger som er annullert`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1, fødselsnummer)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2, fødselsnummer)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3, fødselsnummer)
        annuller(fagsystemId1, fagsystemId2, fagsystemId3)
        assertEquals(emptySet<SpøkelsePeriode>(), gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.mars).somSpøkelsePerioder())
    }

    @Test
    fun `returnerer ikke utbetalinger som er utenfor etterspurt tidsintervall`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1, fødselsnummer)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2, fødselsnummer)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3, fødselsnummer)
        assertEquals(
            SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.februar, tom = 28.februar, grad = 100, organisasjonsnummer = organisasjonsnummer, tags = setOf("Spleis", "VedtakOppdragOgUtbetaling")),
            gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.februar, 28.februar).somSpøkelsePerioder().single(),
        )
    }

    @Test
    fun `returnerer ikke duplikate utbetalinger`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1, fødselsnummer)
        lagreVedtakOppdragOgUtbetaling(1.januar, 31.januar, fagsystemId2, fødselsnummer)
        lagreGammelUtbetaling(1.januar, 31.januar, fagsystemId3, fødselsnummer)

        assertEquals(
            SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.januar, tom = 31.januar, grad = 100, organisasjonsnummer = organisasjonsnummer, tags = setOf("Spleis")),
            gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.januar).somSpøkelsePerioder().single(),
        )
    }

    @Test
    fun `gamle utbetalinger gir versjon ut fra tabellen, mottaker ut fra fagområdet, dagsats og beløp ut fra belop, og gjenstående sykedager`() {
        val vedtakOpprettet = LocalDateTime.of(2021, 3, 1, 12, 0)
        val gammelUtbetalingOpprettet = LocalDateTime.of(2021, 4, 1, 12, 0)
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1, fødselsnummer, dagsats = 2000, beløp = 1000, totalbeløp = 23000)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2, fødselsnummer, fagområde = "SP", dagsats = 3000, beløp = 1500, sykedager = 20, gjenståendeSykedager = 180, opprettet = vedtakOpprettet)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3, fødselsnummer, fagområde = "SPREF", gjenståendeSykedager = 160, opprettet = gammelUtbetalingOpprettet)
        val personidentifikator = Personidentifikator(fødselsnummer)
        assertEquals(
            setOf(
                // Den graderte dagsatsen ligger i belop. Kolonnen dagsats er ugradert eller ukjent, og brukes ikke.
                SpeilV2Linje(personidentifikator, organisasjonsnummer, 1.januar, 31.januar, 100, dagsats = 1000, beløp = 23000),
                SpeilV3Linje(personidentifikator, organisasjonsnummer, PERSON, 1.februar, 28.februar, 100, dagsats = 1500, beløp = 30000, gjenståendeSykedager = 180, utbetaltTidspunkt = vedtakOpprettet.atZone(ZoneId.of("Europe/Oslo")).toInstant()),
                SpeilV1Linje(personidentifikator, organisasjonsnummer, ARBEIDSGIVER, 1.mars, 31.mars, 100, gjenståendeSykedager = 160, utbetaltTidspunkt = gammelUtbetalingOpprettet.atZone(ZoneId.of("Europe/Oslo")).toInstant()),
            ),
            gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.mars).somUtbetalteLinjer().toSet(),
        )
    }

    private val fagsystemId1 = "FAG1"
    private val fagsystemId2 = "FAG2"
    private val fagsystemId3 = "FAG3"
    private val fødselsnummer = "11111111111"
    private val organisasjonsnummer = "999999999"

    private fun annuller(vararg fagsystemIder: String) {
        fagsystemIder.forEach { tbdUtbetalingHåndterer.håndter(utbetalingAnnullert(fødselsnummer = fødselsnummer, arbeidsgiverFagsystemId = it), LocalDateTime.now()) }
    }
}
