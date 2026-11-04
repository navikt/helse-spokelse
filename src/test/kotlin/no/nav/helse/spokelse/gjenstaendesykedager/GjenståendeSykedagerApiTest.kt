package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.AbstractE2ETest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class GjenståendeSykedagerApiTest : AbstractE2ETest() {
    @Test
    fun `svarer med gjenstående sykedager fra siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `ingen vedtak gir INGEN_VEDTAK`() {
        assertGjenståendeSykedager(forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `vedtak før grensen for ny løsning telles ikke med`() {
        lagreVedtak(tom = LocalDate.parse("2022-03-01"), gjenståendeSykedager = 200, sendt = LocalDateTime.parse("2022-03-16T21:53:18"))
        assertGjenståendeSykedager(forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `siste vedtak velges etter tom og ikke etter sendt`() {
        lagreVedtak(tom = LocalDate.parse("2023-03-31"), gjenståendeSykedager = 150, sendt = LocalDateTime.parse("2023-04-01T12:00:00"))
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221, sendt = LocalDateTime.parse("2023-05-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 150, vurdertTilOgMed = "2023-03-31"))
    }

    @Test
    fun `revurdering erstatter tidligere vedtak`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221, sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 225, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 225, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `eldre melding overskriver ikke nyere`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 225, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221, sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 225, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `vedtak uten utbetaling kan være siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 10)
        lagreVedtak(tom = LocalDate.parse("2023-02-28"), gjenståendeSykedager = 0, type = "utbetaling_uten_utbetaling")
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 0, vurdertTilOgMed = "2023-02-28"))
    }

    @Test
    fun `annullert siste vedtak gjør at forrige vedtak gjelder`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        val korrelasjonsId = lagreVedtak(tom = LocalDate.parse("2023-02-28"), gjenståendeSykedager = 200)
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `annullert eneste vedtak gir INGEN_VEDTAK`() {
        val korrelasjonsId = lagreVedtak()
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `annullering sendt før vedtaket påvirker det ikke`() {
        val korrelasjonsId = lagreVedtak(sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-01-15T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `ny utbetaling etter annullering gjør vedtaket gyldig igjen`() {
        val korrelasjonsId = lagreVedtak(sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        lagreVedtak(korrelasjonsId, gjenståendeSykedager = 210, sendt = LocalDateTime.parse("2023-04-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 210, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `vedtak for andre personer telles ikke med`() {
        lagreVedtak(fødselsnummer = "11987654321")
        assertGjenståendeSykedager(forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `senere utbetaling i Infotrygd gir SISTE_UTBETALING_I_INFOTRYGD`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygdperioder = listOf(LocalDate.parse("2023-01-01")..LocalDate.parse("2023-02-28"))
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
    }

    @Test
    fun `eldre utbetaling i Infotrygd påvirker ikke svaret`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        infotrygdperioder = listOf(LocalDate.parse("2022-01-01")..LocalDate.parse("2023-01-31"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `feil fra Infotrygd gir 500`() {
        lagreVedtak()
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
    }

    @Test
    fun `ingen vedtak og utbetalinger i Infotrygd gir SISTE_UTBETALING_I_INFOTRYGD`() {
        infotrygdperioder = listOf(LocalDate.parse("2005-03-01")..LocalDate.parse("2005-06-30"))
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
    }

    @Test
    fun `ingen vedtak og feil fra Infotrygd gir 500`() {
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
    }

    @Test
    fun `teller oppslag per utfall`() {
        assertGjenståendeSykedager(forventetResponseBody = ingenVedtak)
        infotrygdperioder = listOf(LocalDate.parse("2005-03-01")..LocalDate.parse("2005-06-30"))
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
        infotrygdperioder = emptyList()
        lagreVedtak()
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))

        mapOf("INGEN_VEDTAK" to 1.0, "SISTE_UTBETALING_I_INFOTRYGD" to 1.0, "FEIL" to 1.0, "VEDTAK_I_NY_LØSNING" to 1.0).forEach { (utfall, forventet) ->
            assertEquals(forventet, meterRegistry.find("gjenstaende_sykedager_oppslag").tag("utfall", utfall).counter()?.count(), "utfall=$utfall")
        }
    }

    @Test
    fun `ugyldig fødselsnummer gir 400`() {
        assertGjenståendeSykedager(requestBody = """{"fødselsnummer": "1234"}""", forventetHttpStatus = 400, forventetResponseBody = ugyldigForespørsel)
        assertGjenståendeSykedager(requestBody = """{"fødselsnummer": 12345678911}""", forventetHttpStatus = 400, forventetResponseBody = ugyldigForespørsel)
    }

    @Test
    fun `ugyldig body gir 400`() {
        assertGjenståendeSykedager(requestBody = "ikke json", forventetHttpStatus = 400, forventetResponseBody = ugyldigForespørsel)
        assertGjenståendeSykedager(requestBody = "{}", forventetHttpStatus = 400, forventetResponseBody = ugyldigForespørsel)
    }

    @Test
    fun `feil rolle gir 403`() {
        assertGjenståendeSykedager(rolle = "foreldrepenger-les", forventetHttpStatus = 403)
    }

    @Test
    fun `uten token gir 401`() {
        assertGjenståendeSykedager(rolle = null, app = null, forventetHttpStatus = 401)
    }

    @Test
    fun `OpenAPI-specen og Swagger UI er tilgjengelig uten token`() {
        assertApiRequest(path = "openapi/gjenstaende-sykedager.yml", httpMethod = "GET", rolle = null, app = null)
        assertApiRequest(path = "swagger", httpMethod = "GET", rolle = null, app = null)
    }

    private fun lagreVedtak(
        korrelasjonsId: UUID = UUID.randomUUID(),
        fødselsnummer: String = FØDSELSNUMMER,
        tom: LocalDate = LocalDate.parse("2023-01-31"),
        gjenståendeSykedager: Int = 221,
        sendt: LocalDateTime = LocalDateTime.parse("2023-02-01T12:00:00"),
        type: String = "utbetaling_utbetalt",
    ): UUID {
        val fom = tom.withDayOfMonth(1)
        @Language("JSON")
        val json =
            """
            {
              "event": "$type",
              "utbetalingId": "${UUID.randomUUID()}",
              "korrelasjonsId": "$korrelasjonsId",
              "fødselsnummer": "$fødselsnummer",
              "organisasjonsnummer": "999263550",
              "fom": "$fom",
              "tom": "$tom",
              "gjenståendeSykedager": $gjenståendeSykedager,
              "arbeidsgiverOppdrag": {
                "fagsystemId": "${UUID.randomUUID()}",
                "utbetalingslinjer": [{ "fom": "$fom", "tom": "$tom", "grad": 100.0 }]
              },
              "personOppdrag": { "fagsystemId": "${UUID.randomUUID()}", "utbetalingslinjer": [] }
            }
            """
        tbdUtbetalingHåndterer.håndter(objectMapper.readTree(json), sendt)
        return korrelasjonsId
    }

    private fun annuller(
        korrelasjonsId: UUID,
        sendt: LocalDateTime,
    ) {
        @Language("JSON")
        val json =
            """
            {
              "event": "utbetaling_annullert",
              "utbetalingId": "${UUID.randomUUID()}",
              "korrelasjonsId": "$korrelasjonsId",
              "fødselsnummer": "$FØDSELSNUMMER",
              "organisasjonsnummer": "999263550",
              "fom": "2023-01-01",
              "tom": "2023-01-31",
              "arbeidsgiverFagsystemId": "${UUID.randomUUID()}",
              "personFagsystemId": null
            }
            """
        tbdUtbetalingHåndterer.håndter(objectMapper.readTree(json), sendt)
    }

    private fun assertGjenståendeSykedager(
        requestBody: String = """{"fødselsnummer": "$FØDSELSNUMMER"}""",
        forventetHttpStatus: Int = 200,
        forventetResponseBody: String? = null,
        rolle: String? = "aap-les",
        app: String? = "aap",
    ) = assertApiRequest(
        path = "gjenstaende-sykedager",
        requestBody = requestBody,
        forventetHttpStatus = forventetHttpStatus,
        forventetResponseBody = forventetResponseBody,
        rolle = rolle,
        app = app,
    )

    private fun vedtakINyLøsning(
        gjenståendeDager: Int,
        vurdertTilOgMed: String,
    ) = """{"status": "VEDTAK_I_NY_LØSNING", "gjenståendeDager": $gjenståendeDager, "vurdertTilOgMed": "$vurdertTilOgMed"}"""

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private val objectMapper = jacksonObjectMapper()
        private val ingenVedtak = """{"status": "INGEN_VEDTAK"}"""
        private val sisteUtbetalingIInfotrygd = """{"status": "SISTE_UTBETALING_I_INFOTRYGD"}"""
        private val ugyldigForespørsel = """{"feilkode": "UGYLDIG_FORESPØRSEL", "melding": "Forventer en body med fødselsnummer på 11 siffer"}"""
    }
}
