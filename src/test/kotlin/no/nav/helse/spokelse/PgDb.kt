package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers

val databaseContainer = DatabaseContainers.container("spokelse", CleanupStrategy.tables("alle_annulleringer, annullering, gamle_utbetalinger, hendelse, old_utbetaling, old_vedtak, oppdrag, tbdutbetaling_melding, tbdutbetaling_utbetaling, tbdutbetaling_utbetalingslinje, utbetaling, vedtak, vedtak_utbetalingsref"))
