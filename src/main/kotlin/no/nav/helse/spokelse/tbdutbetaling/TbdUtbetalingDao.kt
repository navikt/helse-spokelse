package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
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
                // Sletter alle gamle utbetalingslinjer, erstatter med nye
                listOfNotNull(utbetaling.arbeidsgiverOppdrag?.fagsystemId, utbetaling.personOppdrag?.fagsystemId).forEach { fagsystemId ->
                    transactionalSession.execute(queryOf(slettUtbetalingslinjer, mapOf("fagsystemId" to fagsystemId)))
                }
                // Oppdaterer eller oppretter utbetaling
                transactionalSession.oppdaterEllerOpprettUtbetaling(meldingId, utbetaling)

                // Legger til nye utbetalingslinjer
                utbetaling.arbeidsgiverOppdrag?.let { transactionalSession.leggTilUtbetalingslinjer(meldingId, it) }
                utbetaling.personOppdrag?.let { transactionalSession.leggTilUtbetalingslinjer(meldingId, it) }
            }
        }
    }

    internal fun hentUtbetalinger(fødselsnummer: String): List<Utbetaling> {
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(hentUtbetalinger, mapOf("fodselsnummer" to fødselsnummer)).map { row -> Utbetaling(
                fødselsnummer = fødselsnummer,
                gjenståendeSykedager = row.int("gjenstaaendeSykedager"),
                arbeidsgiverOppdrag = row.stringOrNull("arbeidsgiverFagsystemId")?.let { fagsystemId -> Oppdrag(fagsystemId, session.hentUtbtalingslinjer(fagsystemId)) },
                personOppdrag = row.stringOrNull("personFagsystemId")?.let { fagsystemId -> Oppdrag(fagsystemId, session.hentUtbtalingslinjer(fagsystemId)) }
            )}.asList)
        }
    }

    private fun Session.hentUtbtalingslinjer(fagsystemId: String) = run(queryOf(hentUtbetalingslinjer, mapOf("fagsystemId" to fagsystemId)).map { row -> Utbetalingslinje(
        fom = row.localDate("fom"),
        tom = row.localDate("tom"),
        grad = row.double("grad")
    )}.asList)

    private fun TransactionalSession.leggTilUtbetalingslinjer(meldingId: Long, oppdrag: Oppdrag) {
        oppdrag.utbetalingslinjer.forEach { utbetalingslinje ->
            run(queryOf(leggTilUtbetalingslinje, mapOf(
                "fom" to utbetalingslinje.fom,
                "tom" to utbetalingslinje.tom,
                "grad" to utbetalingslinje.grad,
                "meldingId" to meldingId,
                "fagsystemId" to oppdrag.fagsystemId
            )).asExecute)
        }
    }

    private fun TransactionalSession.oppdaterEllerOpprettUtbetaling(meldingId: Long, utbetaling: Utbetaling) {
        if (oppdatertUtbetaling(meldingId, utbetaling)) return
        sikkerlogg.info("Legger til ny utbtaling")
        opprettUtbetaling(meldingId, utbetaling)
    }

    private fun TransactionalSession.oppdatertUtbetaling(meldingId: Long, utbetaling: Utbetaling): Boolean {
        val finnParameters = mapOf<String, Any?>(
            "personFagsystemId" to (utbetaling.personOppdrag?.fagsystemId ?: "n/a"),
            "arbeidsgiverFagsystemId" to (utbetaling.arbeidsgiverOppdrag?.fagsystemId ?: "n/a")
        )
        val eksisterendeUtbetaling = run(queryOf(finnUtbetaling, finnParameters).map { EksistrendeUtbetaling(
            id = it.long("id"),
            arbeidsgiverFagsystemId = it.stringOrNull("arbeidsgiverFagsystemId"),
            personFagsytemId = it.stringOrNull("personFagsytemId")
        )}.asSingle) ?: return false

        val oppdaterParameters = mapOf<String, Any?>(
            "meldingId" to meldingId,
            "gjenstaaendeSykedager" to utbetaling.gjenståendeSykedager,
            "id" to eksisterendeUtbetaling.id
        )

        run(queryOf(oppdaterUtbetaling, oppdaterParameters).asExecute)

        if (eksisterendeUtbetaling.trengerArbeidsgiverFagsystemId(utbetaling)) {
            run(queryOf(leggTilArbeidsgiverFagsystemId, mapOf(
                "id" to eksisterendeUtbetaling.id,
                "arbeidsgiverFagsystemId" to utbetaling.arbeidsgiverOppdrag?.fagsystemId
            )).asExecute)
        }

        if (eksisterendeUtbetaling.trengerPersonFagsystemId(utbetaling)) {
            run(queryOf(leggTilPersonFagsystemId, mapOf(
                "id" to eksisterendeUtbetaling.id,
                "personFagsytemId" to utbetaling.arbeidsgiverOppdrag?.fagsystemId
            )).asExecute)
        }

        return true
    }

    private fun TransactionalSession.opprettUtbetaling(meldingId: Long, utbetaling: Utbetaling) {
        val parameters = mapOf<String, Any?>(
            "personFagsystemId" to utbetaling.personOppdrag?.fagsystemId,
            "arbeidsgiverFagsystemId" to utbetaling.arbeidsgiverOppdrag?.fagsystemId,
            "meldingId" to meldingId,
            "gjenstaaendeSykedager" to utbetaling.gjenståendeSykedager,
            "fodselsnummer" to utbetaling.fødselsnummer
        )

        run(queryOf(opprettUtbetaling, parameters).asUpdate)
    }

    private class EksistrendeUtbetaling(
        val id: Long,
        val personFagsytemId: String?,
        val arbeidsgiverFagsystemId: String?
    ) {
        fun trengerPersonFagsystemId(utbetaling: Utbetaling) = personFagsytemId == null && utbetaling.personOppdrag?.fagsystemId != null
        fun trengerArbeidsgiverFagsystemId(utbetaling: Utbetaling) = arbeidsgiverFagsystemId != null && utbetaling.arbeidsgiverOppdrag?.fagsystemId != null
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        @Language("PostgreSQL")
        val leggTilMelding = "INSERT INTO tbdUtbetaling_Melding(melding, tidspunkt) VALUES (:melding::json, :tidspunkt)"

        @Language("PostgreSQL")
        val finnUtbetaling = """
            SELECT id, arbeidsgiverFagsystemId, personFagsystemId
            FROM tbdUtbetaling_Utbetaling
            WHERE arbeidsgiverFagsystemId = :arbeidsgiverFagsystemId OR personFagsystemId = :personFagsystemId
        """

        @Language("PostgreSQL")
        val slettUtbetalingslinjer = "DELETE FROM tbdUtbetaling_Utbetalingslinje WHERE fagsystemId = :fagsystemId"

        @Language("PostgreSQL")
        val oppdaterUtbetaling = """
            UPDATE tbdUtbetaling_Utbetaling
            SET kilde = :meldingId, gjenstaaendeSykedager = :gjenstaaendeSykedager
            WHERE id = :id
        """

        @Language("PostgreSQL")
        val leggTilArbeidsgiverFagsystemId = "UPDATE tbdUtbetaling_Utbetaling SET arbeidsgiverFagsystemId = :arbeidsgiverFagsystemId WHERE id = :id"

        @Language("PostgreSQL")
        val leggTilPersonFagsystemId = "UPDATE tbdUtbetaling_Utbetaling SET personFagsystemId = :personFagsystemId WHERE id = :id"

        @Language("PostgreSQL")
        val opprettUtbetaling = """
            INSERT INTO tbdUtbetaling_Utbetaling(kilde, gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, fodselsnummer)
            VALUES (:meldingId, :gjenstaaendeSykedager, :arbeidsgiverFagsystemId, :personFagsystemId, :fodselsnummer)
        """

        @Language("PostgreSQL")
        val leggTilUtbetalingslinje = """
            INSERT INTO tbdUtbetaling_Utbetalingslinje(kilde, fagsystemId, fom, tom, grad)
            VALUES (:meldingId, :fagsystemId, :fom, :tom, :grad)
        """

        @Language("PostgreSQL")
        val hentUtbetalinger = """
            SELECT gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId
            FROM tbdUtbetaling_Utbetaling
            WHERE fodselsnummer = :fodselsnummer
        """

        @Language("PostgreSQL")
        val hentUtbetalingslinjer = """
            SELECT *
            FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId = :fagsystemId
        """
    }
}
