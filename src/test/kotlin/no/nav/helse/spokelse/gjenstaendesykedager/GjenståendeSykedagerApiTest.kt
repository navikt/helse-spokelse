package no.nav.helse.spokelse.gjenstaendesykedager

import io.ktor.http.HttpStatusCode
import no.nav.helse.spokelse.AbstractE2ETest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `feil fra spleis-api gir 500`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygdperioder = listOf(utbetaling("2023-02-01", "2023-02-28"))
        spleisApiSvar = SpleisApiSvar(HttpStatusCode.InternalServerError)
        assertGjenståendeSykedager(forventetHttpStatus = 500)
    }

    @Test
    fun `feil fra Infotrygd gir 500`() {
        lagreVedtak()
        infotrygdperioder = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
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
    fun `Modia personoversikt får slå opp med personoversikt-les`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        assertGjenståendeSykedager(
            rolle = "personoversikt-les",
            app = "modiapersonoversikt-api",
            forventetResponseBody = vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"),
        )
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
    ) = tbdUtbetalingHåndterer.lagreVedtak(korrelasjonsId, fødselsnummer, tom, gjenståendeSykedager, sendt, type)

    private fun annuller(
        korrelasjonsId: UUID,
        sendt: LocalDateTime,
    ) = tbdUtbetalingHåndterer.annuller(korrelasjonsId, sendt)

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
        organisasjonsnummer: String? = null,
    ) = Infotrygdperiode(LocalDate.parse(fom), LocalDate.parse(tom), type, maksdato?.let(LocalDate::parse), organisasjonsnummer)

    private companion object {
        private val ingenSykepenger = """{"grunnlag": "INGEN_SYKEPENGER_SISTE_26_UKER"}"""
        private val manglerMaksdatoIInfotrygd = """{"grunnlag": "MANGLER_MAKSDATO_I_INFOTRYGD"}"""
        private val sisteUtbetalingIInfotrygd = """{"grunnlag": "SISTE_UTBETALING_I_INFOTRYGD", "gjenståendeDager": 87, "vurdertTilOgMed": "2023-02-28"}"""
        private val ugyldigForespørsel = """{"feilkode": "UGYLDIG_FORESPØRSEL", "melding": "Forventer en body med fødselsnummer på 11 siffer"}"""
    }
}
