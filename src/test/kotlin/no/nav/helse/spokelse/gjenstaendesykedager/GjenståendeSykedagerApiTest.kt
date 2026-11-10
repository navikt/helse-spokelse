package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.AbstractE2ETest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class GjenståendeSykedagerApiTest : AbstractE2ETest() {
    @BeforeEach
    fun oppslagsdag() {
        idag = LocalDate.parse("2023-03-01")
    }

    @Test
    fun `svarer med gjenstående sykedager fra siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `ingen vedtak og ingen utbetalinger gir INGEN_SYKEPENGER_SISTE_26_UKER`() {
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
    }

    @Test
    fun `vedtak før grensen for ny løsning telles ikke med`() {
        lagreVedtak(tom = LocalDate.parse("2022-03-01"), gjenståendeSykedager = 200, sendt = LocalDateTime.parse("2022-03-16T21:53:18"))
        idag = LocalDate.parse("2022-03-20")
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
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
    fun `annullert eneste vedtak gir INGEN_SYKEPENGER_SISTE_26_UKER`() {
        val korrelasjonsId = lagreVedtak()
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
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
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
    }

    @Test
    fun `senere utbetaling i Infotrygd gir SISTE_UTBETALING_I_INFOTRYGD`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygdperioder = listOf(utbetaling("2023-01-01", "2023-02-28"))
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
    }

    @Test
    fun `eldre utbetaling i Infotrygd påvirker ikke svaret`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        infotrygdperioder = listOf(utbetaling("2022-01-01", "2023-01-31"))
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `feil fra Infotrygd gir 500`() {
        lagreVedtak()
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
    }

    @Test
    fun `ingen vedtak i ny løsning og feil fra Infotrygd gir 500`() {
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
    }

    @Test
    fun `182 dager etter siste vedtak gir ny rett, 181 dager gjør det ikke`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        idag = LocalDate.parse("2023-07-31")
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
        idag = LocalDate.parse("2023-08-01")
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
    }

    @Test
    fun `182 dager etter siste utbetaling i Infotrygd gir ny rett, 181 dager gjør det ikke`() {
        infotrygdperioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"))
        idag = LocalDate.parse("2027-04-29")
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
        idag = LocalDate.parse("2027-04-30")
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
    }

    @Test
    fun `ny rett regnes fra det som er senest av siste vedtak og siste utbetaling i Infotrygd`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygdperioder = listOf(utbetaling("2023-02-01", "2023-02-28"))
        idag = LocalDate.parse("2023-08-01")
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
    }

    @Test
    fun `ferie i Infotrygd utsetter ikke ny rett`() {
        infotrygdperioder =
            listOf(
                utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"),
                utbetaling("2026-10-31", "2026-11-30", type = "FERIE"),
            )
        idag = LocalDate.parse("2027-04-30")
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
    }

    @Test
    fun `minst 182 dager opphold etter siste vedtak gir Infotrygds maksdato`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-10-15")
        infotrygdperioder = listOf(utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"))
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 23, vurdertTilOgMed = "2025-09-30"))
        infotrygdperioder = listOf(utbetaling("2025-09-01", "2025-09-30"))
        assertGjenståendeSykedager(forventetResponseBody = manglerMaksdatoIInfotrygd)
    }

    @Test
    fun `opphold på 182 dager etter siste vedtak gir Infotrygds maksdato, 181 dager gjør det ikke`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-08-31")
        infotrygdperioder = listOf(utbetaling("2025-08-01", "2025-08-29", maksdato = "2025-09-05"))
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
        infotrygdperioder = listOf(utbetaling("2025-08-02", "2025-08-29", maksdato = "2025-09-05"))
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 5, vurdertTilOgMed = "2025-08-29"))
    }

    @Test
    fun `opphold på minst 182 dager mellom to utbetalinger i Infotrygd etter siste vedtak gir Infotrygds maksdato`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-10-15")
        infotrygdperioder =
            listOf(
                utbetaling("2025-02-01", "2025-02-28"),
                utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"),
            )
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 23, vurdertTilOgMed = "2025-09-30"))
    }

    @Test
    fun `ferie i Infotrygd bryter ikke oppholdet etter siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-10-15")
        infotrygdperioder =
            listOf(
                utbetaling("2025-05-01", "2025-05-31", type = "FERIE"),
                utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"),
            )
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 23, vurdertTilOgMed = "2025-09-30"))
    }

    @Test
    fun `ny rett sjekkes før oppholdet etter siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2026-03-31")
        infotrygdperioder = listOf(utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"))
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
    }

    @Test
    fun `bare utbetalinger i Infotrygd gir gjenstående dager fra Infotrygds maksdato`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygdperioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"))
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
    }

    @Test
    fun `maksdato samme dag som eller før siste utbetalte dag gir 0`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygdperioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-10-30"))
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 0, vurdertTilOgMed = "2026-10-30"))
        infotrygdperioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-10-15"))
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 0, vurdertTilOgMed = "2026-10-30"))
    }

    @Test
    fun `mangler perioden med siste utbetaling maksdato, gir det MANGLER_MAKSDATO_I_INFOTRYGD`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygdperioder =
            listOf(
                utbetaling("2026-09-01", "2026-09-30", maksdato = "2027-06-01"),
                utbetaling("2026-10-01", "2026-10-30"),
            )
        assertGjenståendeSykedager(forventetResponseBody = manglerMaksdatoIInfotrygd)
    }

    @Test
    fun `ferie og andre typer enn utbetalinger er ikke siste utbetaling`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygdperioder =
            listOf(
                utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"),
                utbetaling("2026-10-31", "2026-11-05", type = "FERIE"),
                utbetaling("2026-10-31", "2026-11-06", type = "TILBAKEFØRT"),
            )
        assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
    }

    @Test
    fun `alle utbetalingstypene kan være siste utbetaling`() {
        idag = LocalDate.parse("2026-11-10")
        listOf("UTBETALING", "REDUKSJON_MEDLEM", "ARB_REF", "REDUKSJON_ARB_REF").forEach { type ->
            infotrygdperioder =
                listOf(
                    utbetaling("2026-09-01", "2026-09-30", maksdato = "2026-12-31"),
                    utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13", type = type),
                )
            assertGjenståendeSykedager(forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
        }
    }

    @Test
    fun `teller oppslag per grunnlag`() {
        assertGjenståendeSykedager(forventetResponseBody = ingenSykepenger)
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
        infotrygdperioder = emptyList()
        lagreVedtak()
        assertGjenståendeSykedager(forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
        infotrygdperioder = listOf(utbetaling("2023-02-01", "2023-02-28"))
        assertGjenståendeSykedager(forventetResponseBody = sisteUtbetalingIInfotrygd)
        val personUtenVedtak = """{"fødselsnummer": "11987654321"}"""
        infotrygdperioder = listOf(utbetaling("2023-02-01", "2023-02-28", maksdato = "2023-03-03"))
        assertGjenståendeSykedager(requestBody = personUtenVedtak, forventetResponseBody = maksdatoIInfotrygd(gjenståendeDager = 3, vurdertTilOgMed = "2023-02-28"))
        infotrygdperioder = listOf(utbetaling("2023-02-01", "2023-02-28"))
        assertGjenståendeSykedager(requestBody = personUtenVedtak, forventetResponseBody = manglerMaksdatoIInfotrygd)

        listOf(
            "INGEN_SYKEPENGER_SISTE_26_UKER",
            "FEIL",
            "VEDTAK_I_NY_LØSNING",
            "SISTE_UTBETALING_I_INFOTRYGD",
            "MAKSDATO_I_INFOTRYGD",
            "MANGLER_MAKSDATO_I_INFOTRYGD",
        ).forEach { utfall ->
            assertEquals(1.0, meterRegistry.find("gjenstaende_sykedager_oppslag").tag("utfall", utfall).counter()?.count(), "utfall=$utfall")
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
    ) = """{"grunnlag": "VEDTAK_I_NY_LØSNING", "gjenståendeDager": $gjenståendeDager, "vurdertTilOgMed": "$vurdertTilOgMed"}"""

    private fun maksdatoIInfotrygd(
        gjenståendeDager: Int,
        vurdertTilOgMed: String,
    ) = """{"grunnlag": "MAKSDATO_I_INFOTRYGD", "gjenståendeDager": $gjenståendeDager, "vurdertTilOgMed": "$vurdertTilOgMed"}"""

    private fun utbetaling(
        fom: String,
        tom: String,
        maksdato: String? = null,
        type: String = "UTBETALING",
    ) = Infotrygdperiode(LocalDate.parse(fom), LocalDate.parse(tom), type, maksdato?.let(LocalDate::parse))

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private val objectMapper = jacksonObjectMapper()
        private val ingenSykepenger = """{"grunnlag": "INGEN_SYKEPENGER_SISTE_26_UKER"}"""
        private val manglerMaksdatoIInfotrygd = """{"grunnlag": "MANGLER_MAKSDATO_I_INFOTRYGD"}"""
        private val sisteUtbetalingIInfotrygd = """{"grunnlag": "SISTE_UTBETALING_I_INFOTRYGD"}"""
        private val ugyldigForespørsel = """{"feilkode": "UGYLDIG_FORESPØRSEL", "melding": "Forventer en body med fødselsnummer på 11 siffer"}"""
    }
}
