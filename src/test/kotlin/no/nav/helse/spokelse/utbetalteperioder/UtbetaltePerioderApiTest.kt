package no.nav.helse.spokelse.utbetalteperioder

import io.ktor.http.ContentType
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.tbdutbetaling.Oppdrag
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje
import no.nav.helse.spokelse.tbdutbetaling.utbetalingUtbetalt
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.LocalDateTime

internal class UtbetaltePerioderApiTest : AbstractE2ETest() {
    private companion object {
        private val rolleFor =
            mapOf(
                "utbetalte-perioder" to "spleiselaget-les",
                "utbetalte-perioder-aap" to "aap-les",
                "utbetalte-perioder-dagpenger" to "dagpenger-les",
                "utbetalte-perioder-personoversikt" to "personoversikt-les",
            )

        @JvmStatic
        fun endepunkter() = rolleFor.keys.toList()

        @JvmStatic
        fun ugyldigeRequester() =
            rolleFor.keys.flatMap { endepunkt ->
                listOf(
                    """{"personidentifikatorer": [], "fom": "2024-01-01", "tom": "2024-01-31", "oppløsning": []}""",
                    """{"personidentifikatorer": ["123"], "fom": "2024-01-01", "tom": "2024-01-31", "oppløsning": []}""",
                    """{"personidentifikatorer": ["11111111111"], "tom": "2024-01-31", "oppløsning": []}""",
                    """{"personidentifikatorer": ["11111111111"], "fom": "2024-01-31", "tom": "2024-01-01", "oppløsning": []}""",
                    """ikke json""",
                ).map { Arguments.of(endepunkt, it) }
            } +
                listOf(
                    Arguments.of("utbetalte-perioder", """{"personidentifikatorer": ["11111111111"], "fom": "2024-01-01", "tom": "2024-01-31"}"""),
                    Arguments.of("utbetalte-perioder", """{"personidentifikatorer": ["11111111111"], "fom": "2024-01-01", "tom": "2024-01-31", "oppløsning": ["404"]}"""),
                )
    }

    @ParameterizedTest
    @MethodSource("endepunkter")
    fun `riktig rolle gir 200`(endepunkt: String) {
        assertUtbetaltePerioder(endepunkt, rolle = rolleFor.getValue(endepunkt), forventetHttpStatus = 200)
    }

    @ParameterizedTest
    @MethodSource("endepunkter")
    fun `feil rolle gir 403`(endepunkt: String) {
        assertUtbetaltePerioder(endepunkt, rolle = "foreldrepenger-les", forventetHttpStatus = 403)
    }

    @ParameterizedTest
    @MethodSource("endepunkter")
    fun `ingen rolle gir 403`(endepunkt: String) {
        assertUtbetaltePerioder(endepunkt, rolle = null, forventetHttpStatus = 403)
    }

    @ParameterizedTest
    @MethodSource("endepunkter")
    fun `uten token gir 401`(endepunkt: String) {
        assertUtbetaltePerioder(endepunkt, rolle = null, app = null, forventetHttpStatus = 401)
    }

    @Test
    fun `andre feil gir fortsatt 500`() {
        assertUtbetaltePerioder("utbetalte-perioder-aap", rolle = "aap-les", fom = "2024-01-31", tom = "2024-01-01", forventetHttpStatus = 500)
    }

    @ParameterizedTest
    @ValueSource(strings = ["utbetalte-perioder-aap", "utbetalte-perioder-dagpenger"])
    fun `aap og dagpenger får bare grad, fom og tom, uansett oppløsning`(endepunkt: String) {
        infotrygdperioderMedOgUtenArbeidsgiver()
        assertUtbetaltePerioder(
            endepunkt,
            rolle = rolleFor.getValue(endepunkt),
            oppløsning = """["personidentifikator", "organisasjonsnummer", "grad", "kilde"]""",
            forventetHttpStatus = 200,
            forventetResponseBody = """
                {
                  "utbetaltePerioder": [
                    { "grad": 100, "fom": "2024-01-01", "tom": "2024-01-10" },
                    { "grad": 100, "fom": "2024-01-11", "tom": "2024-01-20" }
                  ]
                }
            """,
        )
    }

    @Test
    fun `personoversikt får organisasjonsnummer, også når det er null, og tags`() {
        infotrygdperioderMedOgUtenArbeidsgiver()
        assertUtbetaltePerioder(
            "utbetalte-perioder-personoversikt",
            rolle = "personoversikt-les",
            forventetHttpStatus = 200,
            forventetResponseBody = """
                {
                  "utbetaltePerioder": [
                    { "organisasjonsnummer": "999999999", "grad": 100, "fom": "2024-01-01", "tom": "2024-01-10", "tags": ["Infotrygd"] },
                    { "organisasjonsnummer": null, "grad": 100, "fom": "2024-01-11", "tom": "2024-01-20", "tags": ["Infotrygd"] }
                  ]
                }
            """,
        )
    }

    @Test
    fun `utbetalte perioder uten oppløsning gir bare fom, tom og tags`() {
        infotrygdperioderMedOgUtenArbeidsgiver()
        assertUtbetaltePerioder(
            "utbetalte-perioder",
            rolle = "spleiselaget-les",
            forventetHttpStatus = 200,
            forventetResponseBody = """
                {
                  "utbetaltePerioder": [
                    { "fom": "2024-01-01", "tom": "2024-01-10", "tags": ["Infotrygd"] },
                    { "fom": "2024-01-11", "tom": "2024-01-20", "tags": ["Infotrygd"] }
                  ]
                }
            """,
        )
    }

    @Test
    fun `utbetalte perioder med oppløsning på organisasjonsnummer gir organisasjonsnummer, også når det er null`() {
        infotrygdperioderMedOgUtenArbeidsgiver()
        assertUtbetaltePerioder(
            "utbetalte-perioder",
            rolle = "spleiselaget-les",
            oppløsning = """["organisasjonsnummer"]""",
            forventetHttpStatus = 200,
            forventetResponseBody = """
                {
                  "utbetaltePerioder": [
                    { "organisasjonsnummer": "999999999", "fom": "2024-01-01", "tom": "2024-01-10", "tags": ["Infotrygd"] },
                    { "organisasjonsnummer": null, "fom": "2024-01-11", "tom": "2024-01-20", "tags": ["Infotrygd"] }
                  ]
                }
            """,
        )
    }

    @Test
    fun `utbetalte perioder med oppløsning på personidentifikator, grad og kilde gir personidentifikator og grad, og holder kildene hver for seg`() {
        infotrygdperioder = listOf(Infotrygdperiode(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-10"), organisasjonsnummer = "999999999"))
        spleisperiode(LocalDate.parse("2024-01-11"), LocalDate.parse("2024-01-31"), organisasjonsnummer = "999999999")
        assertUtbetaltePerioder(
            "utbetalte-perioder",
            rolle = "spleiselaget-les",
            oppløsning = """["personidentifikator", "grad", "kilde"]""",
            forventetHttpStatus = 200,
            forventetResponseBody = """
                {
                  "utbetaltePerioder": [
                    { "personidentifikator": "11111111111", "grad": 100, "fom": "2024-01-01", "tom": "2024-01-10", "tags": ["Infotrygd"] },
                    { "personidentifikator": "11111111111", "grad": 100, "fom": "2024-01-11", "tom": "2024-01-31", "tags": ["Spleis", "TbdUtbetaling"] }
                  ]
                }
            """,
        )
    }

    @Test
    fun `ingen perioder gir tom liste`() {
        assertUtbetaltePerioder(
            "utbetalte-perioder-aap",
            rolle = "aap-les",
            forventetHttpStatus = 200,
            forventetResponseBody = """{ "utbetaltePerioder": [] }""",
        )
    }

    @ParameterizedTest
    @MethodSource("ugyldigeRequester")
    fun `ugyldig request gir 500 med problem+json`(
        endepunkt: String,
        requestBody: String,
    ) {
        assertApiRequest(
            path = endepunkt,
            requestBody = requestBody,
            forventetHttpStatus = 500,
            forventetContentType = ContentType.Application.ProblemJson,
            rolle = rolleFor.getValue(endepunkt),
            app = "konsument",
        )
    }

    private fun infotrygdperioderMedOgUtenArbeidsgiver() {
        infotrygdperioder =
            listOf(
                Infotrygdperiode(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-10"), organisasjonsnummer = "999999999"),
                Infotrygdperiode(LocalDate.parse("2024-01-11"), LocalDate.parse("2024-01-20"), organisasjonsnummer = null),
            )
    }

    private fun spleisperiode(
        fom: LocalDate,
        tom: LocalDate,
        organisasjonsnummer: String,
    ) = tbdUtbetalingHåndterer.håndter(
        utbetalingUtbetalt(
            fødselsnummer = "11111111111",
            organisasjonsnummer = organisasjonsnummer,
            arbeidsgiverOppdrag = Oppdrag("arbeid", listOf(Utbetalingslinje(fom, tom, 100.0))),
            fom = fom,
            tom = tom,
        ),
        LocalDateTime.parse("2024-02-01T12:00:00"),
    )

    private fun assertUtbetaltePerioder(
        endepunkt: String,
        rolle: String?,
        forventetHttpStatus: Int,
        app: String? = "konsument",
        fom: String = "2024-01-01",
        tom: String = "2024-01-31",
        oppløsning: String = "[]",
        forventetResponseBody: String? = null,
    ) = assertApiRequest(
        path = endepunkt,
        requestBody = """{"personidentifikatorer": ["11111111111"], "fom": "$fom", "tom": "$tom", "oppløsning": $oppløsning}""",
        forventetHttpStatus = forventetHttpStatus,
        forventetResponseBody = forventetResponseBody,
        forventetContentType = forventetResponseBody?.let { ContentType.Application.Json },
        rolle = rolle,
        app = app,
    )
}
