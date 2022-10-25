package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somUtbetalingDTO
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

internal class TbdUtbtalingApi(private val tbdUtbetalingDao: TbdUtbetalingDao) {

    private val brukUtbetalingerEtter = LocalDateTime.parse("2022-03-16T21:53:18")

    private fun utbetalinger(fødselsnummer: String, fom: LocalDate?) = tbdUtbetalingDao.hentUtbetalinger(fødselsnummer, fom).filter { it.sistUtbetalt > brukUtbetalingerEtter }.also {
        if (it.isNotEmpty()) sikkerlogg.info("Fant ${it.size} utbetalinger for $fødselsnummer fra tbd.utbetaling.")
    }

    fun hentFpVedtak(fødselsnummer: String, fom: LocalDate?) = utbetalinger(fødselsnummer, fom).somFpVedtak()
    fun hentUtbetalingDTO(fødselsnumre: List<String>) = fødselsnumre.map { fødselsnummer -> utbetalinger(fødselsnummer, null).somUtbetalingDTO() }.flatten()

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}
