package no.nav.helse.spokelse.tbdutbetaling

import no.nav.sykepenger.libs.logging.loggInfo
import java.time.LocalDate
import java.time.LocalDateTime

internal class TbdUtbetalingApi(
    private val tbdUtbetalingDao: TbdUtbetalingDao,
) {
    private val brukUtbetalingerEtter = LocalDateTime.parse("2022-03-16T21:53:18")

    internal fun utbetalinger(
        fødselsnummer: String,
        fom: LocalDate?,
        tom: LocalDate?,
    ) = tbdUtbetalingDao.hentUtbetalinger(fødselsnummer, fom, tom).filter { it.sistUtbetalt > brukUtbetalingerEtter }.also {
        if (it.isNotEmpty()) loggInfo("Fant ${it.size} utbetalinger fra tbd.utbetaling", "fødselsnummer" to fødselsnummer)
    }
}
