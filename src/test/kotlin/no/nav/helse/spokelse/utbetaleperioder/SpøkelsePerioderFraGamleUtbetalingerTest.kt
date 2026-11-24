package no.nav.helse.spokelse.utbetaleperioder

import no.nav.helse.spokelse.*
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somSpøkelsePerioder
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somUtbetalteLinjer
import no.nav.helse.spokelse.personoversikt.Mottaker.ARBEIDSGIVER
import no.nav.helse.spokelse.personoversikt.Mottaker.PERSON
import no.nav.helse.spokelse.personoversikt.UtbetaltLinje
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V1
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V2
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V3
import no.nav.helse.spokelse.tbdutbetaling.utbetalingAnnullert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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
    fun `gamle utbetalinger gir versjon ut fra tabellen og mottaker ut fra fagområdet`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1, fødselsnummer)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2, fødselsnummer, fagområde = "SP")
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3, fødselsnummer, fagområde = "SPREF")
        val personidentifikator = Personidentifikator(fødselsnummer)
        assertEquals(
            setOf(
                UtbetaltLinje(SPEIL_V2, personidentifikator, organisasjonsnummer, null, 1.januar, 31.januar, 100, usikkerGrad = false),
                UtbetaltLinje(SPEIL_V3, personidentifikator, organisasjonsnummer, PERSON, 1.februar, 28.februar, 100, usikkerGrad = false),
                UtbetaltLinje(SPEIL_V1, personidentifikator, organisasjonsnummer, ARBEIDSGIVER, 1.mars, 31.mars, 100, usikkerGrad = false),
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
