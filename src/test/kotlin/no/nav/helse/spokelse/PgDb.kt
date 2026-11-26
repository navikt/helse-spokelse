package no.nav.helse.spokelse

import no.nav.sykepenger.libs.postgres.TestDatabase
import java.util.concurrent.LinkedBlockingQueue

internal val databaseContainer = TestDatabaser("alle_annulleringer, annullering, gamle_utbetalinger, gjenstående_sykedager, hendelse, old_utbetaling, old_vedtak, oppdrag, tbdutbetaling_melding, tbdutbetaling_utbetaling, tbdutbetaling_utbetalingslinje, tbd_vedtak, utbetaling, vedtak, vedtak_utbetalingsref")

internal class TestDatabaser(
    private val tabeller: String,
) {
    private val ledige = LinkedBlockingQueue<TestDatabase>()

    fun nyTilkobling(): TestDatabase = ledige.poll() ?: TestDatabase.start().also { it.migrer() }

    fun droppTilkobling(database: TestDatabase) {
        database.dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("truncate table $tabeller restart identity cascade") }
        }
        ledige.offer(database)
    }
}
