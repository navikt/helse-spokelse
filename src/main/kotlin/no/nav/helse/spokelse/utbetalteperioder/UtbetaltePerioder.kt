package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.felles.Infotrygd
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import java.time.LocalDate

internal class UtbetaltePerioder(
    private val infotrygd: Infotrygd,
    tbdUtbetalingApi: TbdUtbetalingApi,
    gamleUtbetalingerDao: GamleUtbetalingerDao,
) {
    private val spleis =
        Spleis(
            tbdUtbetalingApi = tbdUtbetalingApi,
            gamleUtbetalingerDao = gamleUtbetalingerDao,
        )

    internal suspend fun hent(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate,
        groupBy: Set<GroupBy>,
    ): List<SpøkelsePeriode> {
        require(personidentifikatorer.isNotEmpty()) { "Det må sendes med minst én personidentifikator" }
        check(tom >= fom) { "Ugyldig periode $fom - $tom" }
        return Gruppering(
            groupBy = groupBy,
            infotrygd = infotrygd.hent(personidentifikatorer, fom, tom),
            spleis = spleis.hent(personidentifikatorer, fom, tom),
        ).gruppér()
    }
}
