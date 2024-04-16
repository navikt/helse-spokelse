package no.nav.helse.spokelse.utbetaleperioder

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.*
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somSpøkelsePerioder
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class SpøkelsePerioderFraGamleUtbetalingerTest : AbstractE2ETest() {

    @Test
    fun `hente utbetalte perioder fra gamle tabeller`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3)
        assertEquals(
            setOf(
                SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.januar, tom = 31.januar, grad = 100, organisasjonsnummer = organisasjonsnummer, setOf("Spleis", "OldVedtakOgOldUtbetaling")),
                SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.februar, tom = 28.februar, grad = 100, organisasjonsnummer = organisasjonsnummer, setOf("Spleis", "VedtakOppdragOgUtbetaling")),
                SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.mars, tom = 31.mars, grad = 100, organisasjonsnummer = organisasjonsnummer,setOf("Spleis", "GamleUtbetalinger"))
            ), gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.mars).somSpøkelsePerioder()
        )
    }

    @Test
    fun `returnerer ikke utbetalinger som er annullert`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3)
        annuller(fagsystemId1, fagsystemId2, fagsystemId3)
        assertEquals(emptySet<SpøkelsePeriode>(), gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.mars).somSpøkelsePerioder())
    }

    @Test
    fun `returnerer ikke utbetalinger som er utenfor etterspurt tidsintervall`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1)
        lagreVedtakOppdragOgUtbetaling(1.februar, 28.februar, fagsystemId2)
        lagreGammelUtbetaling(1.mars, 31.mars, fagsystemId3)
        assertEquals(
            SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.februar, tom = 28.februar, grad = 100, organisasjonsnummer = organisasjonsnummer, tags = setOf("Spleis", "VedtakOppdragOgUtbetaling")),
            gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.februar, 28.februar).somSpøkelsePerioder().single()
        )
    }

    @Test
    fun `returnerer ikke duplikate utbetalinger`() {
        lagreOldVedtakOgOldUtbetaling(1.januar, 31.januar, fagsystemId1)
        lagreVedtakOppdragOgUtbetaling(1.januar, 31.januar, fagsystemId2)
        lagreGammelUtbetaling(1.januar, 31.januar, fagsystemId3)

        assertEquals(
            SpøkelsePeriode(personidentifikator = Personidentifikator(fødselsnummer), fom = 1.januar, tom = 31.januar, grad = 100, organisasjonsnummer = organisasjonsnummer, tags = setOf("Spleis")),
            gamleUtbetalingerDao.hentUtbetalinger(fødselsnummer, 1.januar, 31.januar).somSpøkelsePerioder().single()
        )
    }

    private val fagsystemId1 = "FAG1"
    private val fagsystemId2 = "FAG2"
    private val fagsystemId3 = "FAG3"
    private val fødselsnummer = "11111111111"
    private val organisasjonsnummer = "999999999"
    private fun lagreOldVedtakOgOldUtbetaling(fom: LocalDate, tom: LocalDate, fagsystemId: String) {
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        val vedtaksperiodeId = UUID.randomUUID()
        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)
        lagreVedtakDao.save(
            OldVedtak(
                vedtaksperiodeId = vedtaksperiodeId,
                fødselsnummer = fødselsnummer,
                orgnummer = organisasjonsnummer,
                utbetalinger = listOf(
                    OldUtbetaling(
                        fom = fom,
                        tom = tom,
                        grad = 100.0,
                        dagsats = 1,
                        totalbeløp = 1,
                        beløp = 1
                    )
                ),
                opprettet = LocalDateTime.now(),
                forbrukteSykedager = 1,
                gjenståendeSykedager = 1,
                dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding)
            )
        )
    }

    private fun lagreVedtakOppdragOgUtbetaling(fom: LocalDate, tom: LocalDate, fagsystemId: String) {
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        utbetaltDao.opprett(
            Vedtak(
                hendelseId = UUID.randomUUID(),
                fødselsnummer = fødselsnummer,
                orgnummer = organisasjonsnummer,
                dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding),
                oppdrag = listOf(Vedtak.Oppdrag("mottaker", "bjeff", fagsystemId,1, listOf(Vedtak.Oppdrag.Utbetalingslinje(fom, tom, 1, 1,100.0, 1)))),
                fom = fom,
                tom = tom,
                forbrukteSykedager = 1,
                gjenståendeSykedager = 1,
                opprettet = LocalDateTime.now()
            )
        )
    }

    private fun lagreGammelUtbetaling(fom: LocalDate, tom: LocalDate, fagsystemId: String) {
        sessionOf(dataSource).use { session ->
            val query = """
                INSERT INTO gamle_utbetalinger (fodselsnummer, orgnummer, opprettet, fom, tom, grad, fagsystem_id, fagomrade, gjenstaende_sykedager)
                VALUES('$fødselsnummer', '$organisasjonsnummer', now(), '$fom', '$tom', 100.0, '$fagsystemId', 'JA', 1)
            """
            session.run(queryOf(query).asExecute)
        }
    }

    private fun annuller(vararg fagsystemIder: String) {
        fagsystemIder.forEach { annulleringDao.insertAnnullering(fødselsnummer, "Ja", it, LocalDate.now(), LocalDate.now(), "Ja") }
        fagsystemIder.forEach { gamleUtbetalingerDao.annullering(1L, Annullering(it, null)) }
    }
}
