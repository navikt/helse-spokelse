package no.nav.helse.spokelse.personoversikt

import no.nav.helse.spokelse.felles.Infotrygd
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gamleutbetalinger.GammelUtbetaling.Companion.somUtbetalteLinjer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somUtbetalteLinjer
import java.time.LocalDate

internal class Personoversikt(
    private val infotrygd: Infotrygd,
    private val tbdUtbetalingApi: TbdUtbetalingApi,
    private val gamleUtbetalingerDao: GamleUtbetalingerDao,
) {
    internal suspend fun hent(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate,
    ): List<Sykepengeperiode> {
        val speil =
            personidentifikatorer.flatMap { personidentifikator ->
                tbdUtbetalingApi.utbetalinger(personidentifikator.toString(), fom, tom).somUtbetalteLinjer() +
                    gamleUtbetalingerDao.hentUtbetalinger(personidentifikator.toString(), fom, tom).somUtbetalteLinjer()
            }
        return (speil + infotrygd.hentUtbetalteLinjer(personidentifikatorer, fom, tom)).somSykepengeperioder()
    }
}
