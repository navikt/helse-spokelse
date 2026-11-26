package no.nav.helse.spokelse.personoversikt

import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.Infotrygd
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somUtbetalteLinjer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somUtbetalteLinjer
import no.nav.helse.spokelse.tbdvedtak.TbdVedtakDao
import java.time.LocalDate
import javax.sql.DataSource

internal class Personoversikt(
    private val infotrygd: Infotrygd,
    private val tbdUtbetalingApi: TbdUtbetalingApi,
    private val gamleUtbetalingerDao: GamleUtbetalingerDao,
    private val dataSource: () -> DataSource,
) {
    internal suspend fun hent(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate,
    ): List<Sykepengeperiode> {
        val speil =
            personidentifikatorer.flatMap { personidentifikator ->
                tbdUtbetalingApi.utbetalinger(personidentifikator.toString(), fom, tom).somUtbetalteLinjer(vedtak(personidentifikator)) +
                    gamleUtbetalingerDao.hentUtbetalinger(personidentifikator.toString(), fom, tom).somUtbetalteLinjer()
            }
        return (speil + infotrygd.hentUtbetalteLinjer(personidentifikatorer, fom, tom)).somSykepengeperioder()
    }

    private fun vedtak(personidentifikator: Personidentifikator) = sessionOf(dataSource()).use { session -> TbdVedtakDao(session).hent(personidentifikator.toString()) }
}
