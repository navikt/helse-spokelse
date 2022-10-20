package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somUtbetalingDTO
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

internal class TbdUtbtalingApi(private val tbdUtbetalingDao: TbdUtbetalingDao) {

    private val brukUtbetalingerEtter = LocalDateTime.parse("2022-03-16T21:53:18")

    private fun utbetalinger(fødselsnummer: String) = tbdUtbetalingDao.hentUtbetalinger(fødselsnummer).filter { it.sistUtbetalt > brukUtbetalingerEtter }.also {
        sikkerlogg.info("Fant ${it.size} utbetalinger for $fødselsnummer fra tbd.utbetaling.")
    }

    fun hentFpVedtak(fødselsnummer: String) = utbetalinger(fødselsnummer).somFpVedtak()
    fun hentUtbetalingDTO(fødselsnumre: List<String>) = fødselsnumre.map { fødselsnummer -> utbetalinger(fødselsnummer).somUtbetalingDTO() }.flatten()

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}
