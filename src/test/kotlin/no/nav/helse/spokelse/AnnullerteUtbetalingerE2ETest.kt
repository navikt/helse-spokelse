package no.nav.helse.spokelse

import no.nav.helse.spokelse.Events.annulleringEvent
import no.nav.helse.spokelse.Events.formater
import no.nav.helse.spokelse.Events.genererFagsystemId
import no.nav.helse.spokelse.Events.inntektsmeldingEvent
import no.nav.helse.spokelse.Events.sendtSøknadNavEvent
import no.nav.helse.spokelse.Events.utbetaltEvent
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class AnnullerteUtbetalingerE2ETest : AbstractE2ETest() {

    @Test
    fun `Annullering av utbetaling med full refusjon`() {
        val fødselsnummer = "12345678912"
        val orgnummer = "985748784"
        val arbeidsgiverFagsystemId = genererFagsystemId()
        val tidspunkt = LocalDateTime.now()
        val fom = LocalDate.parse("2021-01-01")
        val tom = LocalDate.parse("2021-02-15")
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = ingenUtbetalingerResponse(fødselsnummer)
        )

        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        rapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))
        rapid.sendTestMessage(
            utbetaltEvent(
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                grad = 70.0,
                fom = fom,
                tom = tom,
                hendelser = listOf(sykmelding, søknad, inntektsmelding),
                arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
                personFagsystemId = null,
                tidspunkt = tidspunkt
            )
        )

        @Language("JSON")
        val forventetFørAnnullering = """
        [{"fødselsnummer":"12345678912","fom":"2021-01-01","tom":"2021-02-15","grad":70.0,"gjenståendeSykedager":216,"utbetaltTidspunkt":"${tidspunkt.formater()}","refusjonstype":"REFUSJON_TIL_ARBEIDSGIVER"}]
        """

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = forventetFørAnnullering
        )

        rapid.sendTestMessage(
            annulleringEvent(
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
                fom = fom,
                tom = tom
            )
        )

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = ingenUtbetalingerResponse(fødselsnummer)
        )
    }

    @Test
    fun `Annullering av utbetaling med ingen refusjon`() {
        val fødselsnummer = "12345678912"
        val orgnummer = "985748784"
        val personFagsystemId = genererFagsystemId()
        val tidspunkt = LocalDateTime.now()
        val fom = LocalDate.parse("2021-01-01")
        val tom = LocalDate.parse("2021-02-15")
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = ingenUtbetalingerResponse(fødselsnummer)
        )

        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        rapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))
        rapid.sendTestMessage(
            utbetaltEvent(
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                grad = 70.0,
                fom = fom,
                tom = tom,
                hendelser = listOf(sykmelding, søknad, inntektsmelding),
                arbeidsgiverFagsystemId = null,
                personFagsystemId = personFagsystemId,
                tidspunkt = tidspunkt
            )
        )

        @Language("JSON")
        val forventetFørAnnullering = """
        [{"fødselsnummer":"12345678912","fom":"2021-01-01","tom":"2021-02-15","grad":70.0,"gjenståendeSykedager":216,"utbetaltTidspunkt":"${tidspunkt.formater()}","refusjonstype":"REFUSJON_TIL_PERSON"}]
        """

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = forventetFørAnnullering
        )

        rapid.sendTestMessage(
            annulleringEvent(
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                arbeidsgiverFagsystemId = null,
                personFagsystemId = personFagsystemId,
                fom = fom,
                tom = tom
            )
        )

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = ingenUtbetalingerResponse(fødselsnummer)
        )
    }

    @Test
    fun `Annullering av utbetaling med delvis refusjon`() {
        val fødselsnummer = "12345678912"
        val orgnummer = "985748784"
        val personFagsystemId = genererFagsystemId()
        val arbeidsgiverFagsystemId = genererFagsystemId()
        val tidspunkt = LocalDateTime.now()
        val fom = LocalDate.parse("2021-01-01")
        val tom = LocalDate.parse("2021-02-15")
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = ingenUtbetalingerResponse(fødselsnummer)
        )

        rapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        rapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))
        rapid.sendTestMessage(
            utbetaltEvent(
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                grad = 70.0,
                fom = fom,
                tom = tom,
                hendelser = listOf(sykmelding, søknad, inntektsmelding),
                arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
                personFagsystemId = personFagsystemId,
                tidspunkt = tidspunkt
            )
        )

        @Language("JSON")
        val forventetFørAnnullering = """
        [
          {"fødselsnummer":"12345678912","fom":"2021-01-01","tom":"2021-02-15","grad":70.0,"gjenståendeSykedager":216,"utbetaltTidspunkt":"${tidspunkt.formater()}","refusjonstype":"REFUSJON_TIL_ARBEIDSGIVER"},
          {"fødselsnummer":"12345678912","fom":"2021-01-01","tom":"2021-02-15","grad":70.0,"gjenståendeSykedager":216,"utbetaltTidspunkt":"${tidspunkt.formater()}","refusjonstype":"REFUSJON_TIL_PERSON"}
        ]
        """

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = forventetFørAnnullering
        )

        rapid.sendTestMessage(
            annulleringEvent(
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
                personFagsystemId = personFagsystemId,
                fom = fom,
                tom = tom
            )
        )

        assertApiRequest(
            path = "utbetalinger",
            httpMethod = "POST",
            requestBody = """["$fødselsnummer"]""",
            forventetResponseBody = ingenUtbetalingerResponse(fødselsnummer)
        )
    }

    private companion object {
        @Language("JSON")
        fun ingenUtbetalingerResponse(fødselsnummer: String) = """
        [{"fødselsnummer":"$fødselsnummer","fom":null,"tom":null,"grad":0.0,"gjenståendeSykedager":null,"utbetaltTidspunkt":null,"refusjonstype":null}]
        """
    }
}
