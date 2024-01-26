package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.gamlevedtak.HentVedtakDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somSpøkelsePerioder
import java.time.LocalDate

internal class Spleis(private val tbdUtbetalingApi: TbdUtbetalingApi, private val hentVedtakDao: HentVedtakDao) {
    fun hent(personidentifikatorer: Set<Personidentifikator>, tidligsteSluttdato: LocalDate, senesteStartdato: LocalDate): List<SpøkelsePeriode> {
        return personidentifikatorer
            .associateWith { tbdUtbetalingApi.utbetalinger(it.toString(), tidligsteSluttdato, senesteStartdato) }
            .mapValues { (_, utbetalinger) ->
                utbetalinger.somSpøkelsePerioder()
            }.mapValues { (personidentifikator, spøkelsePerioder) ->
                spøkelsePerioder + hentVedtakDao.hentSpøkelsePerioder(personidentifikator.toString(), tidligsteSluttdato, senesteStartdato)
            }.values.flatten()
    }
}
