package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal class TbdUtbetalingDao(
    private val dataSource: DataSource
) {
    internal fun lagreMelding(melding: Melding): Long {
        return sessionOf(dataSource = dataSource, returnGeneratedKey = true).use { session ->
            requireNotNull(session.run(queryOf(leggTilMelding, mapOf("melding" to "$melding", "tidspunkt" to melding.tidspunkt)).asUpdateAndReturnGeneratedKey)) {
                "Klart ikke å lagre melding"
            }
        }
    }

    internal fun lagreUtbetaling(meldingId: Long, utbetaling: Utbetaling) {
        sessionOf(dataSource).use { session ->
            session.transaction { transactionalSession ->
                // Sletter eventuell eksisterende utbetaling (og tilhørende utbetalingslinjer)
                transactionalSession.execute(queryOf(slettUtbetaling, mapOf("korrelasjonsId" to utbetaling.korrelasjonsId)))

                // Oppretter utbetalingen på ny
                transactionalSession.opprettUtbetaling(meldingId, utbetaling)

                // Legger til nye utbetalingslinjer
                utbetaling.arbeidsgiverOppdrag?.let { transactionalSession.leggTilUtbetalingslinjer(utbetaling, meldingId, it) }
                utbetaling.personOppdrag?.let { transactionalSession.leggTilUtbetalingslinjer(utbetaling, meldingId, it) }
            }
        }
    }

    internal fun hentUtbetalinger(fødselsnummer: String): List<Utbetaling> {
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(hentUtbetalinger, mapOf("fodselsnummer" to fødselsnummer)).map { row ->

                val arbeidsgiverFagystemId = row.stringOrNull("arbeidsgiverFagsystemId")
                val arbeidsgiverUtbetalingslinjer = arbeidsgiverFagystemId?.let { session.hentUtbetalingslinjer(it) } ?: emptyList()
                val personFagsystemId = row.stringOrNull("personFagsystemId")
                val personUtbetalingslinjer = personFagsystemId?.let { session.hentUtbetalingslinjer(it) } ?: emptyList()

                if (arbeidsgiverUtbetalingslinjer.isEmpty() && personUtbetalingslinjer.isEmpty()) null

                else Utbetaling(
                    fødselsnummer = fødselsnummer,
                    korrelasjonsId = row.uuid("korrelasjonsId"),
                    gjenståendeSykedager = row.int("gjenstaaendeSykedager"),
                    arbeidsgiverOppdrag = arbeidsgiverUtbetalingslinjer.takeUnless { it.isEmpty() }?.let { Oppdrag(arbeidsgiverFagystemId!!, it) },
                    personOppdrag = personUtbetalingslinjer.takeUnless { it.isEmpty() }?.let { Oppdrag(personFagsystemId!!, it) }
                )
            }.asList)
        }
    }

    internal fun annuller(meldingId: Long, annullering: Annullering) {
        // TODO: Legge til meldingId på utbetaling?
        sessionOf(dataSource).use { session ->
            listOfNotNull(annullering.arbeidsgiverFagsystemId, annullering.personFagsystemId).forEach { fagsystemId ->
                session.run(queryOf(slettUtbetalingslinjer, mapOf("fagsystemId" to fagsystemId)).asUpdate)
            }
        }
    }

    private fun Session.hentUtbetalingslinjer(fagsystemId: String) = run(queryOf(hentUtbetalingslinjer, mapOf("fagsystemId" to fagsystemId)).map { row -> Utbetalingslinje(
        fom = row.localDate("fom"),
        tom = row.localDate("tom"),
        grad = row.double("grad")
    )}.asList)

    private fun TransactionalSession.leggTilUtbetalingslinjer(utbetaling: Utbetaling, meldingId: Long, oppdrag: Oppdrag) {
        oppdrag.utbetalingslinjer.forEach { utbetalingslinje ->
            run(queryOf(leggTilUtbetalingslinje, mapOf(
                "fom" to utbetalingslinje.fom,
                "tom" to utbetalingslinje.tom,
                "grad" to utbetalingslinje.grad,
                "meldingId" to meldingId,
                "fagsystemId" to oppdrag.fagsystemId,
                "utbetaling" to utbetaling.korrelasjonsId
            )).asExecute)
        }
    }

    private fun TransactionalSession.opprettUtbetaling(meldingId: Long, utbetaling: Utbetaling) {
        val parameters = mapOf<String, Any?>(
            "personFagsystemId" to utbetaling.personOppdrag?.fagsystemId,
            "korrelasjonsId" to utbetaling.korrelasjonsId,
            "arbeidsgiverFagsystemId" to utbetaling.arbeidsgiverOppdrag?.fagsystemId,
            "meldingId" to meldingId,
            "gjenstaaendeSykedager" to utbetaling.gjenståendeSykedager,
            "fodselsnummer" to utbetaling.fødselsnummer
        )

        run(queryOf(opprettUtbetaling, parameters).asUpdate)
    }

    private companion object {

        @Language("PostgreSQL")
        val leggTilMelding = "INSERT INTO tbdUtbetaling_Melding(melding, tidspunkt) VALUES (:melding::json, :tidspunkt)"

        @Language("PostgreSQL")
        val slettUtbetaling = "DELETE FROM tbdUtbetaling_Utbetaling WHERE korrelasjonsId = :korrelasjonsId"

        @Language("PostgreSQL")
        val opprettUtbetaling = """
            INSERT INTO tbdUtbetaling_Utbetaling(kilde, gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, fodselsnummer, korrelasjonsId)
            VALUES (:meldingId, :gjenstaaendeSykedager, :arbeidsgiverFagsystemId, :personFagsystemId, :fodselsnummer, :korrelasjonsId)
        """

        @Language("PostgreSQL")
        val leggTilUtbetalingslinje = """
            INSERT INTO tbdUtbetaling_Utbetalingslinje(kilde, fagsystemId, fom, tom, grad, utbetaling)
            VALUES (:meldingId, :fagsystemId, :fom, :tom, :grad, :utbetaling)
        """

        @Language("PostgreSQL")
        val hentUtbetalinger = """
            SELECT gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, korrelasjonsId
            FROM tbdUtbetaling_Utbetaling
            WHERE fodselsnummer = :fodselsnummer
        """

        @Language("PostgreSQL")
        val hentUtbetalingslinjer = """
            SELECT *
            FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId = :fagsystemId
        """

        @Language("PostgreSQL")
        val slettUtbetalingslinjer = """
            DELETE FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId = :fagsystemId
        """
    }
}
