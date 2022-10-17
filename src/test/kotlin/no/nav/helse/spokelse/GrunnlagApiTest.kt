package no.nav.helse.spokelse

import no.nav.helse.spokelse.Events.inntektsmeldingEvent
import no.nav.helse.spokelse.Events.sendtSøknadNavEvent
import org.intellij.lang.annotations.Language
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
        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        rapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"
        val fom = LocalDate.of(2020, 4, 1)
        val tom = LocalDate.of(2020, 4, 6)
        val grad = 50.0
        val vedtattTidspunkt = LocalDateTime.of(2020, 4, 11, 10, 0)
        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)
        vedtakDao.save(
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
    }

    @Test
    fun `skriver vedtak til db`() {
        val fnr = "01010145679"
        val orgnummer = "123456789"
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        rapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))
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
        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
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
}
