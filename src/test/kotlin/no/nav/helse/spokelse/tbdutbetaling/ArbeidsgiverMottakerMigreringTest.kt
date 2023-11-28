package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.PgDb
import no.nav.helse.spokelse.januar
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

internal class ArbeidsgiverMottakerMigreringTest {

    @Test
    fun `Migrerer inn arbeidsgiverMottaker på utbetaling`() {
        PgDb.start()
        val dataSource = PgDb.connection()
        val dao = TbdUtbetalingDao { dataSource }

        val antall = 10//_000

        for (i in 1..antall) {
            dao.lagreTestUtbetaling(i)
        }

        assertEquals(antall, dataSource.antallUtbetalinger)

        val millis = measureTimeMillis {
            dataSource.flywayConfiguration.target(MigrationVersion.fromVersion("14")).load().migrate()
        }

        println("Tok $millis millis å migrere inn arbeidsgiverMottaker på $antall utbetalinger")

        for (i in 1..antall) {
            assertEquals(i.orgnr, dataSource.arbeidsgiverMottaker(i))
        }

        PgDb.hardReset()
    }

    private val DataSource.flywayConfiguration get() = Flyway
        .configure()
        .dataSource(this)
        .cleanDisabled(false)
        .locations("classpath:db/migration", "classpath:db/test-migration")

    private val DataSource.antallUtbetalinger get() = "SELECT count(*) AS antall FROM tbdUtbetaling_Utbetaling".let { sql ->
        sessionOf(this).use { session ->
            session.run(queryOf(sql).map { it.int("antall") }.asSingle)
        } ?: 0
    }

    private fun DataSource.arbeidsgiverMottaker(nummer: Int) = "SELECT arbeidsgiverMottaker FROM tbdUtbetaling_Utbetaling WHERE fodselsnummer='${nummer.fnr}'".let { sql ->
        sessionOf(this).use { session ->
            session.run(queryOf(sql).map { it.string("arbeidsgiverMottaker") }.asSingle)
        } ?: throw IllegalStateException("Fant ikke arbeidsgiverMottaker for $nummer")
    }

    private fun TbdUtbetalingDao.lagreTestUtbetaling(nummer: Int) {
        val meldingId = lagreTestMelding(nummer)

        val utbetaling = Utbetaling(
            fødselsnummer = nummer.fnr,
            korrelasjonsId = UUID.randomUUID(),
            gjenståendeSykedager = 100,
            arbeidsgiverOppdrag = Oppdrag("ArbeidsgiverOppdrag_$nummer", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0))),
            personOppdrag = Oppdrag("PersonOppdrag_$nummer", listOf(Utbetalingslinje(1.januar, 31.januar, 100.0))),
            sistUtbetalt = LocalDateTime.now()
        )
        lagreUtbetaling(meldingId, utbetaling)
    }

    private fun TbdUtbetalingDao.lagreTestMelding(nummer: Int): Long {
        @Language("JSON")
        val melding = """
            {
              "@event_name": "utbetaling_utbetalt",
              "arbeidsgiverOppdrag": {
                "mottaker": "${nummer.orgnr}"
              }
            }
        """
        return lagreMelding(Melding(
            melding = melding,
            meldingSendt = LocalDateTime.now(),
            type = "utbetaling_utbetalt",
            fødselsnummer = nummer.fnr
        ))
    }

    private companion object {
        private val Int.fnr get() = "$this".padStart(11, '0')
        private val Int.orgnr get() = "$this".padStart(9, '0')
    }
}
