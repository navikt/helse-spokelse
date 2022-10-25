package no.nav.helse.spokelse

import no.nav.helse.spokelse.HentVedtakDao.Companion.filtrer
import no.nav.helse.spokelse.HentVedtakDao.Companion.harData
import no.nav.helse.spokelse.HentVedtakDao.VedtakRow
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class GrunnlagApiTest : AbstractE2ETest() {

    @Test
    fun `skriver gammelt vedtak til db`() {
        val fnr = "01010145678"
        val orgnummer = "123456789"
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"
        val fom = LocalDate.of(2020, 4, 1)
        val tom = LocalDate.of(2020, 4, 6)
        val grad = 50.0
        val vedtattTidspunkt = LocalDateTime.of(2020, 4, 11, 10, 0)
        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)
        lagreVedtakDao.save(
            OldVedtak(
                vedtaksperiodeId = vedtaksperiodeId,
                fødselsnummer = fnr,
                orgnummer = orgnummer,
                opprettet = vedtattTidspunkt,
                utbetalinger = listOf(
                    OldUtbetaling(
                        fom = fom,
                        tom = tom,
                        grad = grad,
                        dagsats = 123,
                        beløp = 321,
                        totalbeløp = 456
                    )
                ),
                forbrukteSykedager = 1,
                gjenståendeSykedager = 2,
                dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding)
            )
        )

        @Language("JSON")
        val forventetFørAnnullering = """
        [{"vedtaksreferanse":"VNDG2PFPMNB4FKMC4ORASZ2JJ4","utbetalinger":[{"fom":"2020-04-01","tom":"2020-04-06","grad":50.0}],"vedtattTidspunkt":"2020-04-11T10:00:00"}]
        """

        assertApiRequest(
            path = "grunnlag?fodselsnummer=$fnr",
            httpMethod = "GET",
            forventetResponseBody = forventetFørAnnullering
        )

        assertApiRequest(
            path = "grunnlag?fodselsnummer=$fnr&fom=2020-04-06",
            httpMethod = "GET",
            forventetResponseBody = forventetFørAnnullering
        )

        assertApiRequest(
            path = "grunnlag?fodselsnummer=$fnr&fom=2020-04-07",
            httpMethod = "GET",
            forventetResponseBody = "[]"
        )
    }

    @Test
    fun `skriver vedtak til db`() {
        val fnr = "01010145679"
        val orgnummer = "123456789"
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ5"
        val fom = LocalDate.of(2020, 4, 1)
        val tom = LocalDate.of(2020, 4, 6)
        val vedtattTidspunkt = LocalDateTime.of(2020, 4, 11, 10, 0)

        utbetaltDao.opprett(Vedtak(
            hendelseId = UUID.randomUUID(),
            fødselsnummer = fnr,
            orgnummer = orgnummer,
            dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding),
            oppdrag = listOf(oppdrag(fnr, fagsystemId, "SPREF", fom, tom)),
            fom = fom,
            tom = tom,
            forbrukteSykedager = 32,
            gjenståendeSykedager = 216,
            opprettet = vedtattTidspunkt
        ))

        @Language("JSON")
        val forventetFørAnnullering = """
        [{"vedtaksreferanse":"VNDG2PFPMNB4FKMC4ORASZ2JJ5","utbetalinger":[{"fom":"2020-04-01","tom":"2020-04-06","grad":70.0}],"vedtattTidspunkt":"2020-04-11T10:00:00"}]
        """

        assertApiRequest(
            path = "grunnlag?fodselsnummer=$fnr",
            httpMethod = "GET",
            forventetResponseBody = forventetFørAnnullering
        )
    }

    @Test
    fun `skriver vedtak til db uten inntektsmelding`() {
        val fnr = "01010145679"
        val orgnummer = "123456789"
        val (sykmelding, søknad, _) = nyeDokumenter()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ6"
        val fom = LocalDate.of(2020, 4, 1)
        val tom = LocalDate.of(2020, 4, 6)
        val vedtattTidspunkt = LocalDateTime.of(2020, 4, 11, 10, 0)

        utbetaltDao.opprett(Vedtak(
            hendelseId = UUID.randomUUID(),
            fødselsnummer = fnr,
            orgnummer = orgnummer,
            dokumenter = Dokumenter(sykmelding, søknad, null),
            oppdrag = listOf(oppdrag(fnr, fagsystemId, "SPREF", fom, tom)),
            fom = fom,
            tom = tom,
            forbrukteSykedager = 32,
            gjenståendeSykedager = 216,
            opprettet = vedtattTidspunkt
        ))

        @Language("JSON")
        val forventetFørAnnullering = """
        [{"vedtaksreferanse":"VNDG2PFPMNB4FKMC4ORASZ2JJ6","utbetalinger":[{"fom":"2020-04-01","tom":"2020-04-06","grad":70.0}],"vedtattTidspunkt":"2020-04-11T10:00:00"}]
        """

        assertApiRequest(
            path = "grunnlag?fodselsnummer=$fnr",
            httpMethod = "GET",
            forventetResponseBody = forventetFørAnnullering
        )
    }

    @Test
    fun `kall til grunnlag uten fnr gir 400`() {
        assertApiRequest(
            path = "grunnlag",
            httpMethod = "GET",
            forventetHttpStatus = 400
        )
    }

    @Test
    fun `kall til grunnlag med ugyldig fom gir 400`() {
        assertApiRequest(
            path = "grunnlag?fodselsnummer=01010145679&fom=29.08.1990",
            httpMethod = "GET",
            forventetHttpStatus = 400
        )
    }

    @Test
    fun `dropper oppslag mot gamle tabeller om man spør om nyere data enn tabellene inneholder`() {
        assertTrue(harData(null))
        assertTrue(harData(LocalDate.parse("2022-03-15")))
        assertTrue(harData(LocalDate.parse("2022-03-16")))
        assertFalse(harData(LocalDate.parse("2022-03-17")))
    }

    @Test
    fun `filtrerer bort vedtak basert på fom`() {
        var vedtak = listOf(vedtak(2.januar, 15.januar))
        assertEquals(vedtak, vedtak.filtrer(null))
        assertEquals(vedtak, vedtak.filtrer(1.januar))
        assertEquals(vedtak, vedtak.filtrer(15.januar))
        assertEquals(emptyList<VedtakRow>(), vedtak.filtrer(16.januar))
        assertEquals(emptyList<VedtakRow>(), vedtak.filtrer(17.januar))

        val januar = vedtak(1.januar, 31.januar)
        val mars = vedtak(1.mars, 31.mars)
        vedtak = listOf(januar, mars)

        assertEquals(vedtak, vedtak.filtrer(null))
        assertEquals(vedtak, vedtak.filtrer(LocalDate.parse("2017-12-31")))
        assertEquals(vedtak, vedtak.filtrer(1.januar))
        assertEquals(listOf(mars), vedtak.filtrer(1.februar))
        assertEquals(listOf(mars), vedtak.filtrer(28.februar))
        assertEquals(listOf(mars), vedtak.filtrer(1.mars))
        assertEquals(listOf(mars), vedtak.filtrer(31.mars))
        assertEquals(emptyList<VedtakRow>(), vedtak.filtrer(1.april))
    }

    private companion object {
        private fun vedtak(fom: LocalDate, tom: LocalDate) = VedtakRow("fagsystemId", LocalDateTime.now(), fom, tom, 100.0)
    }
}
