package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

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

    private fun assertUtbetaltePerioder(
        endepunkt: String,
        rolle: String?,
        forventetHttpStatus: Int,
        app: String? = "konsument",
        fom: String = "2024-01-01",
        tom: String = "2024-01-31",
    ) = assertApiRequest(
        path = endepunkt,
        requestBody = """{"personidentifikatorer": ["11111111111"], "fom": "$fom", "tom": "$tom", "oppløsning": []}""",
        forventetHttpStatus = forventetHttpStatus,
        rolle = rolle,
        app = app,
    )
}
