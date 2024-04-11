package no.nav.helse.spokelse.gamlevedtak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingObserver
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.LocalDate
import javax.sql.DataSource

internal class AnnulleringDao(private val dataSource: () -> DataSource): TbdUtbetalingObserver {
    fun insertAnnullering(fødselsnummer: String, mottaker: String, fagsystemId: String, fom: LocalDate, tom: LocalDate, fagområde: String): Int {
        @Language("PostgreSQL")
        val query = """
INSERT INTO annullering(fodselsnummer, mottaker, fagsystem_id, fom, tom, fagomrade)
    VALUES(:fodselsnummer, :mottaker, :fagsystem_id, :fom, :tom, :fagomrade)
    ON CONFLICT DO NOTHING;
                """

        return sessionOf(dataSource()).use { session ->
            session.run(queryOf(query, mapOf(
                "fodselsnummer" to fødselsnummer,
                "mottaker" to mottaker,
                "fagsystem_id" to fagsystemId,
                "fom" to maxOf(fom, LocalDate.ofYearDay(100, 1)),
                "tom" to tom,
                "fagomrade" to fagområde
            )).asUpdate)
        }
    }

    override fun utbetaling(meldingId: Long, utbetaling: Utbetaling) {
        // Nye utbetalinger er ikke aktuelt å lagre, det er kun annulleringer av gamle utbetalinger som er viktig å få med seg.
    }

    override fun annullering(meldingId: Long, annullering: Annullering) {
        // TODO: Lagre en "enklere" tabell med kun fagsystemId'er og migrere inn de som ligger i "annullering" og begynne å inserte nye her.
        sikkerLogg.info("Annullering av fagsystemId'er arbeidsgiver=${annullering.arbeidsgiverFagsystemId}, person=${annullering.personFagsystemId}}")
    }

    private companion object {
        val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }
}
