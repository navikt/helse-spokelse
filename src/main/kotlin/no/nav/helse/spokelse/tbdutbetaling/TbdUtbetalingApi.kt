package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somFpVedtak
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somUtbetalingDTO
import org.slf4j.LoggerFactory

internal class TbdUtbtalingApi(
    env: Map<String, String>,
    private val tbdUtbetalingDao: TbdUtbetalingDao
){
    private val erDev = env.getOrDefault("NAIS_CLUSTER_NAME","n/a") == "dev-fss"

    fun hentFpVedtak(fødselsnummer: String) = hentFailsafe(fødselsnummer) { it.somFpVedtak() }
    fun hentUtbetalingDTO(fødselsnumre: List<String>) = fødselsnumre.map { fødselsnummer -> hentFailsafe(fødselsnummer) { it.somUtbetalingDTO()} }.flatten()

    private fun utbetalinger(fødselsnummer: String) = when {
        erDev || fødselsnummer.startsWith("31") -> tbdUtbetalingDao.hentUtbetalinger(fødselsnummer).also {
            sikkerlogg.info("Fant ${it.size} utbetalinger for $fødselsnummer fra tbd.utbetaling.")
        }
        else -> emptyList()
    }

    private fun <T>hentFailsafe(fødselsnummer: String, mapper: (utbetalinger: List<Utbetaling>) -> List<T>) = try {
        val mapped = mapper(utbetalinger(fødselsnummer))
        if (erDev) mapped
        else emptyList()
    } catch (exception: Exception) {
        sikkerlogg.error("Feil ved henting av utbetalinger for $fødselsnummer", exception)
        emptyList()
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}
