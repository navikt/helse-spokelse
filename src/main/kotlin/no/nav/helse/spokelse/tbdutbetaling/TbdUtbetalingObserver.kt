package no.nav.helse.spokelse.tbdutbetaling

internal interface TbdUtbetalingObserver {
    fun utbetaling(meldingId: Long, utbetaling: Utbetaling)
    fun annullering(meldingId: Long, annullering: Annullering)
}
