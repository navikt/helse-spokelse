package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.tbdutbetaling.Melding
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class GjenståendeSykedagerApiTest : AbstractE2ETest() {
    @Test
    fun `svarer med gjenstående sykedager fra siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 221, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `ingen vedtak gir 404`() {
        assertGjenståendeSykedager(forventetHttpStatus = 404, forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `vedtak før grensen for ny løsning telles ikke med`() {
        lagreVedtak(tom = LocalDate.parse("2022-03-01"), gjenståendeSykedager = 200, sendt = LocalDateTime.parse("2022-03-16T21:53:18"))
        assertGjenståendeSykedager(forventetHttpStatus = 404, forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `siste vedtak velges etter tom og ikke etter sendt`() {
        lagreVedtak(tom = LocalDate.parse("2023-03-31"), gjenståendeSykedager = 150, sendt = LocalDateTime.parse("2023-04-01T12:00:00"))
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221, sendt = LocalDateTime.parse("2023-05-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 150, "gjelderTilOgMed": "2023-03-31"}""")
    }

    @Test
    fun `revurdering erstatter tidligere vedtak`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221, sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 225, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 225, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `eldre melding overskriver ikke nyere`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 225, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        lagreVedtak(korrelasjonsId, tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221, sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 225, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `vedtak uten utbetaling kan være siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 10)
        lagreVedtak(tom = LocalDate.parse("2023-02-28"), gjenståendeSykedager = 0, type = "utbetaling_uten_utbetaling")
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 0, "gjelderTilOgMed": "2023-02-28"}""")
    }

    @Test
    fun `annullert siste vedtak gjør at forrige vedtak gjelder`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        val korrelasjonsId = lagreVedtak(tom = LocalDate.parse("2023-02-28"), gjenståendeSykedager = 200)
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 221, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `annullert eneste vedtak gir 404`() {
        val korrelasjonsId = lagreVedtak()
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        assertGjenståendeSykedager(forventetHttpStatus = 404, forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `annullering sendt før vedtaket påvirker det ikke`() {
        val korrelasjonsId = lagreVedtak(sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-01-15T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 221, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `ny utbetaling etter annullering gjør vedtaket gyldig igjen`() {
        val korrelasjonsId = lagreVedtak(sendt = LocalDateTime.parse("2023-02-01T12:00:00"))
        annuller(korrelasjonsId, sendt = LocalDateTime.parse("2023-03-01T12:00:00"))
        lagreVedtak(korrelasjonsId, gjenståendeSykedager = 210, sendt = LocalDateTime.parse("2023-04-01T12:00:00"))
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 210, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `vedtak for andre personer telles ikke med`() {
        lagreVedtak(fødselsnummer = "11987654321")
        assertGjenståendeSykedager(forventetHttpStatus = 404, forventetResponseBody = ingenVedtak)
    }

    @Test
    fun `senere utbetaling i Infotrygd gir 404`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygdsvar = infotrygdperiode(tom = "2023-02-28")
        assertGjenståendeSykedager(
            forventetHttpStatus = 404,
            forventetResponseBody = """{"feilkode": "SISTE_UTBETALING_I_INFOTRYGD", "melding": "Infotrygd har en utbetaling etter siste vedtak i ny løsning"}""",
        )
    }

    @Test
    fun `eldre utbetaling i Infotrygd påvirker ikke svaret`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        infotrygdsvar = infotrygdperiode(tom = "2023-01-31")
        assertGjenståendeSykedager(forventetResponseBody = """{"gjenståendeSykedager": 221, "gjelderTilOgMed": "2023-01-31"}""")
    }

    @Test
    fun `feil fra Infotrygd gir 500`() {
        lagreVedtak()
        infotrygdsvar = null
        assertGjenståendeSykedager(forventetHttpStatus = 500)
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

    private fun lagreVedtak(
        korrelasjonsId: UUID = UUID.randomUUID(),
        fødselsnummer: String = FØDSELSNUMMER,
        tom: LocalDate = LocalDate.parse("2023-01-31"),
        gjenståendeSykedager: Int = 221,
        sendt: LocalDateTime = LocalDateTime.parse("2023-02-01T12:00:00"),
        type: String = "utbetaling_utbetalt",
    ): UUID {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}", sendt, type, fødselsnummer))
        gjenståendeSykedagerDao.lagreVedtak(meldingId, Vedtak(korrelasjonsId, fødselsnummer, tom.withDayOfMonth(1), tom, gjenståendeSykedager, sendt))
        return korrelasjonsId
    }

    private fun annuller(
        korrelasjonsId: UUID,
        sendt: LocalDateTime,
    ) {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding("{}", sendt, "utbetaling_annullert", FØDSELSNUMMER))
        gjenståendeSykedagerDao.annuller(meldingId, korrelasjonsId, sendt)
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

    private fun infotrygdperiode(tom: String) =
        """{"utbetaltePerioder": [{"personidentifikator": "$FØDSELSNUMMER", "fom": "2023-01-01", "tom": "$tom", "grad": 100}]}"""

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private val ingenVedtak = """{"feilkode": "INGEN_VEDTAK", "melding": "Fant ingen vedtak for personen i ny løsning"}"""
        private val ugyldigForespørsel = """{"feilkode": "UGYLDIG_FORESPØRSEL", "melding": "Forventer en body med fødselsnummer på 11 siffer"}"""
    }
}
