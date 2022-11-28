package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import javax.sql.DataSource

class AnnulleringDao(private val dataSource: DataSource) {
    fun insertAnnullering(fødselsnummer: String, mottaker: String, fagsystemId: String, fom: LocalDate, tom: LocalDate, fagområde: String): Int {
        @Language("PostgreSQL")
        val query = """
INSERT INTO annullering(fodselsnummer, mottaker, fagsystem_id, fom, tom, fagomrade)
    VALUES(:fodselsnummer, :mottaker, :fagsystem_id, :fom, :tom, :fagomrade)
    ON CONFLICT DO NOTHING;
                """

        return sessionOf(dataSource).use { session ->
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
}
