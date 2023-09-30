package no.nav.helse.spokelse

import kotlinx.coroutines.runBlocking
import no.nav.helse.spokelse.Events.genererFagsystemId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class UtbetalingerApiTest : AbstractE2ETest() {

    private val nå = "2023-09-30T16:28:31.123"

    @Test
    fun `finner sykepengeperioder for fnr`() = runBlocking {
        val fødselsnummer = "01010101010"

        lagreOldVedtak(fødselsnummer, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))

        @Language("JSON")
        val forventet = """
        [{
            "fødselsnummer": "01010101010",
            "fom": "1970-01-01",
            "tom": "1970-01-01",
            "grad": 100.0,
            "gjenståendeSykedager": 238,
            "utbetaltTidspunkt": "$nå",
            "refusjonstype": "REFUSJON_TIL_ARBEIDSGIVER"
        }]
        """

        assertUtbetalinger(listOf(fødselsnummer), forventet)

    }

    @Test
    fun `har data for 1 person, spør om utbetalinger for to fødselsnumre`() {
        val fødselsnummer1 = "01010101010"
        val fødselsnummer2 = "01010101011"

        lagreOldVedtak(fødselsnummer1, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))

        @Language("JSON")
        val forventet = """
        [{
            "fødselsnummer": "01010101010",
            "fom": "1970-01-01",
            "tom": "1970-01-01",
            "grad": 100.0,
            "gjenståendeSykedager": 238,
            "utbetaltTidspunkt": "$nå",
            "refusjonstype": "REFUSJON_TIL_ARBEIDSGIVER"
        }, {
            "fødselsnummer": "01010101011",
            "fom": null,
            "tom": null,
            "grad": 0.0,
            "gjenståendeSykedager": null,
            "utbetaltTidspunkt": null,
            "refusjonstype": null
        }]
        """

        assertUtbetalinger(listOf(fødselsnummer1, fødselsnummer2), forventet)
    }

    @Test
    fun `har data for 2 personer, spør om utbetalinger for 3 fødselsnumre`() {
        val fødselsnummer1 = "01010101010"
        val fødselsnummer2 = "01010101011"
        val fødselsnummer3 = "01010101012"

        lagreOldVedtak(fødselsnummer1, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))
        lagreOldVedtak(fødselsnummer3, perioder = listOf(
            LocalDate.of(1970, 1, 1).rangeTo(LocalDate.of(1970, 1, 1))
        ))

        @Language("JSON")
        val forventet = """
        [{
            "fødselsnummer": "01010101010",
            "fom": "1970-01-01",
            "tom": "1970-01-01",
            "grad": 100.0,
            "gjenståendeSykedager": 238,
            "utbetaltTidspunkt": "$nå",
            "refusjonstype": "REFUSJON_TIL_ARBEIDSGIVER"
        }, {
            "fødselsnummer": "01010101011",
            "fom": null,
            "tom": null,
            "grad": 0.0,
            "gjenståendeSykedager": null,
            "utbetaltTidspunkt": null,
            "refusjonstype": null
        }, {
            "fødselsnummer": "01010101012",
            "fom": "1970-01-01",
            "tom": "1970-01-01",
            "grad": 100.0,
            "gjenståendeSykedager": 238,
            "utbetaltTidspunkt": "$nå",
            "refusjonstype": "REFUSJON_TIL_ARBEIDSGIVER"
        }]
        """

        assertUtbetalinger(listOf(fødselsnummer1, fødselsnummer2, fødselsnummer3), forventet)
    }

    private fun assertUtbetalinger(fødselsnumre: List<String>, forventet: String) = assertApiRequest(
        path = "utbetalinger",
        httpMethod = "POST",
        requestBody = "${fødselsnumre.map { "\"$it\"" }}",
        forventetResponseBody = forventet
    )

    private fun lagreOldVedtak(
        fødselsnummer: String,
        fagsystemId: String = genererFagsystemId(),
        perioder: List<ClosedRange<LocalDate>>
    ) {
        val vedtaksperiodeId = UUID.randomUUID()
        val orgnummer = "98765432"
        lagreVedtakDao.save(
            OldVedtak(
                vedtaksperiodeId, fødselsnummer, orgnummer, perioder.map {
                    OldUtbetaling(
                        it.start,
                        it.endInclusive,
                        100.0,
                        1337,
                        1337,
                        9001
                    )
                }, LocalDateTime.parse(nå), 10, 238,
                Dokumenter(
                    Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Sykmelding),
                    Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Søknad),
                    null
                )
            )
        )
        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)
    }
}
