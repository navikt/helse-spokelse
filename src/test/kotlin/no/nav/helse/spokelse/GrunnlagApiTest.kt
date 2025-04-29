package no.nav.helse.spokelse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao.Companion.harData
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
            fødselsnummer = fnr,
            forventetResponseBody = forventetFørAnnullering
        )

        assertApiRequest(
            fødselsnummer = fnr,
            fom = "2020-04-06",
            forventetResponseBody = forventetFørAnnullering
        )

        assertApiRequest(
            fødselsnummer = fnr,
            fom = "2020-04-07",
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
            fødselsnummer = fnr,
            forventetResponseBody = forventetFørAnnullering
        )
    }

    @Test
    fun `lagrer gamle og nye vedtak med laaaaangt orgnummer`() {
        val fnr = "01010145679"
        val orgnummer = "123456789123456789"
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

        lagreVedtakDao.save(
            OldVedtak(
                vedtaksperiodeId = UUID.randomUUID(),
                fødselsnummer = fnr,
                orgnummer = orgnummer,
                opprettet = vedtattTidspunkt,
                utbetalinger = listOf(
                    OldUtbetaling(
                        fom = fom,
                        tom = tom,
                        grad = 100.0,
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
            fødselsnummer = fnr,
            forventetResponseBody = forventetFørAnnullering
        )
    }

    @Test
    fun `kall til grunnlag uten fnr gir 400`() {
        assertApiRequest(
            forventetHttpStatus = 400
        )
    }

    @Test
    fun `kall til grunnlag med ugyldig fom gir 400`() {
        assertApiRequest(
            fødselsnummer = "01010145679",
            fom = "29.08.1990",
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
    fun `ikke tilgang`() {
        // Feil rolle
        assertApiRequest(
            fødselsnummer = "01010145679",
            rolle = "aap-les",
            forventetHttpStatus = 500 // burde jo vært noe annet da
        )
        // Ingen rolle
        assertApiRequest(
            fødselsnummer = "01010145679",
            rolle = null,
            forventetHttpStatus = 500 // burde jo vært noe annet da
        )

        // Ikke noe token
        assertApiRequest(
            fødselsnummer = "01010145679",
            rolle = null,
            app = null,
            forventetHttpStatus = 401
        )
    }

    private fun assertApiRequest(fødselsnummer: String? = null, fom: String? = null, forventetResponseBody: String? = null, forventetHttpStatus: Int = 200, rolle: String? = listOf("foreldrepenger-les", "k9-les").random(), app: String? = "fpsak") {
        val parametre = mapOf("fodselsnummer" to fødselsnummer, "fom" to fom).filterValues { it != null }.mapValues { (_, verdi) -> verdi!! }
        val postBody = jacksonObjectMapper().writeValueAsString(parametre)
        assertApiRequest(
            path = "grunnlag",
            rolle = rolle,
            app = app,
            requestBody = postBody,
            forventetResponseBody = forventetResponseBody,
            forventetHttpStatus = forventetHttpStatus
        )
    }
}
