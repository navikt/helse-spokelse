package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

internal class TbdUtbetalingDao(
    private val dataSource: () -> DataSource
) {
    internal fun lagreMelding(melding: Melding): Long {
        return sessionOf(dataSource = dataSource(), returnGeneratedKey = true).use { session ->
            val parameters = mapOf(
                "melding" to "$melding",
                "sendt" to melding.meldingSendt,
                "type" to melding.type,
                "fodselsnummer" to melding.fødselsnummer
            )
            requireNotNull(session.run(queryOf(leggTilMelding, parameters).asUpdateAndReturnGeneratedKey)) {
                "Klart ikke å lagre melding"
            }
        }
    }

    internal fun lagreUtbetaling(meldingId: Long, utbetaling: Utbetaling) {
        sessionOf(dataSource()).use { session ->
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

    internal fun hentUtbetalinger(fødselsnummer: String, fom: LocalDate? = null, tom: LocalDate? = null): List<Utbetaling> {
        return sessionOf(dataSource()).use { session ->
            session.run(queryOf(hentUtbetalinger, mapOf("fodselsnummer" to fødselsnummer)).map { row ->

                val arbeidsgiverFagystemId = row.stringOrNull("arbeidsgiverFagsystemId")
                val arbeidsgiverUtbetalingslinjer = arbeidsgiverFagystemId?.let { session.hentUtbetalingslinjer(it, fom, tom) } ?: emptyList()
                val personFagsystemId = row.stringOrNull("personFagsystemId")
                val personUtbetalingslinjer = personFagsystemId?.let { session.hentUtbetalingslinjer(it, fom, tom) } ?: emptyList()

                if (arbeidsgiverUtbetalingslinjer.isEmpty() && personUtbetalingslinjer.isEmpty()) null

                else Utbetaling(
                    fødselsnummer = fødselsnummer,
                    korrelasjonsId = row.uuid("korrelasjonsId"),
                    gjenståendeSykedager = row.int("gjenstaaendeSykedager"),
                    arbeidsgiverOppdrag = arbeidsgiverUtbetalingslinjer.takeUnless { it.isEmpty() }?.let { Oppdrag(arbeidsgiverFagystemId!!, it) },
                    personOppdrag = personUtbetalingslinjer.takeUnless { it.isEmpty() }?.let { Oppdrag(personFagsystemId!!, it) },
                    sistUtbetalt = row.localDateTime("sistUtbetalt")
                )
            }.asList)
        }
    }

    internal fun annuller(meldingId: Long, annullering: Annullering) {
        sessionOf(dataSource()).use { session ->
            session.transaction { transactionalSession ->
                annullering.arbeidsgiverFagsystemId?.let { arbeidsgiverFagsystemId ->
                    transactionalSession.run(queryOf(slettUtbetalingslinjer, mapOf(
                        "fagsystemId" to arbeidsgiverFagsystemId
                    )).asUpdate)
                    transactionalSession.run(queryOf(arbeidsgiverUtbetalingAnnullert, mapOf(
                        "fagsystemId" to arbeidsgiverFagsystemId,
                        "meldingId" to meldingId
                    )).asUpdate)
                }
                annullering.personFagsystemId?.let { personFagsystemId ->
                    transactionalSession.run(queryOf(slettUtbetalingslinjer, mapOf(
                        "fagsystemId" to personFagsystemId
                    )).asUpdate)
                    transactionalSession.run(queryOf(personUtbetalingAnnullert, mapOf(
                        "fagsystemId" to personFagsystemId,
                        "meldingId" to meldingId
                    )).asUpdate)
                }
            }
        }
    }

    internal fun arbeidsgiverutbetalinger(tidsrom: Pair<Int, ChronoUnit>) =
        helsesjekk("arbeidsgiverfagsystemid IS NOT NULL AND arbeidsgiverannuleringskilde IS NULL", tidsrom)
    internal fun arbeidsgiverannulleringer(tidsrom: Pair<Int, ChronoUnit>) =
        helsesjekk("arbeidsgiverannuleringskilde IS NOT NULL", tidsrom)
    internal fun personutbetalinger(tidsrom: Pair<Int, ChronoUnit>) =
        helsesjekk("personfagsystemid IS NOT NULL AND personannuleringskilde IS NULL", tidsrom)
    internal fun personannulleringer(tidsrom: Pair<Int, ChronoUnit>) =
        helsesjekk("personannuleringskilde IS NOT NULL", tidsrom)
    private fun helsesjekk(where: String, tidsrom: Pair<Int, ChronoUnit>): Int {
        check(tidsrom.first >= 1) { "Ugyldig tidsrom ${tidsrom.first} ${tidsrom.second}" }
        @Language("PostgresSQL")
        val statement = "SELECT count(1) FROM tbdutbetaling_utbetaling WHERE $where AND sistutbetalt > now() - INTERVAL '${tidsrom.first} ${tidsrom.second}'"
        return sessionOf(dataSource()).use { session ->
            session.run(queryOf(statement).map { row -> row.int(1) }.asSingle) ?: 0
        }
    }
    private fun Session.hentUtbetalingslinjer(fagsystemId: String, fom: LocalDate?, tom: LocalDate?) = run(queryOf(hentUtbetalingslinjer(fom, tom), mapOf("fagsystemId" to fagsystemId, "fom" to fom, "tom" to tom)).map { row -> Utbetalingslinje(
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
            "fodselsnummer" to utbetaling.fødselsnummer,
            "sistUtbetalt" to utbetaling.sistUtbetalt
        )

        run(queryOf(opprettUtbetaling, parameters).asUpdate)
    }

    private companion object {

        @Language("PostgreSQL")
        val leggTilMelding = "INSERT INTO tbdUtbetaling_Melding(melding, sendt, type, fodselsnummer) VALUES (:melding::json, :sendt, :type, :fodselsnummer)"

        @Language("PostgreSQL")
        val slettUtbetaling = "DELETE FROM tbdUtbetaling_Utbetaling WHERE korrelasjonsId = :korrelasjonsId"

        @Language("PostgreSQL")
        val opprettUtbetaling = """
            INSERT INTO tbdUtbetaling_Utbetaling(kilde, gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, fodselsnummer, korrelasjonsId, sistUtbetalt)
            VALUES (:meldingId, :gjenstaaendeSykedager, :arbeidsgiverFagsystemId, :personFagsystemId, :fodselsnummer, :korrelasjonsId, :sistUtbetalt)
        """

        @Language("PostgreSQL")
        val leggTilUtbetalingslinje = """
            INSERT INTO tbdUtbetaling_Utbetalingslinje(kilde, fagsystemId, fom, tom, grad, utbetaling)
            VALUES (:meldingId, :fagsystemId, :fom, :tom, :grad, :utbetaling)
        """

        @Language("PostgreSQL")
        val hentUtbetalinger = """
            SELECT gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, korrelasjonsId, sistUtbetalt
            FROM tbdUtbetaling_Utbetaling
            WHERE fodselsnummer = :fodselsnummer
            AND (arbeidsgiverAnnuleringskilde IS NULL OR personAnnuleringskilde IS NULL)
        """

        @Language("PostgreSQL")
        fun hentUtbetalingslinjer(fom: LocalDate?, tom: LocalDate?) = """
            SELECT *
            FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId = :fagsystemId
        """.let { when (fom) {
            null -> it
            else -> "$it AND tom >= :fom"
        }}.let { when (tom) {
            null -> it
            else -> "$it AND NOT fom > :tom"
        }}

        @Language("PostgreSQL")
        val slettUtbetalingslinjer = """
            DELETE FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId = :fagsystemId
        """

        @Language("PostgreSQL")
        val arbeidsgiverUtbetalingAnnullert = """
            UPDATE tbdUtbetaling_Utbetaling
            SET arbeidsgiverAnnuleringskilde = :meldingId
            WHERE arbeidsgiverFagsystemId = :fagsystemId
        """

        @Language("PostgreSQL")
        val personUtbetalingAnnullert = """
            UPDATE tbdUtbetaling_Utbetaling
            SET personAnnuleringskilde   = :meldingId
            WHERE personFagsystemId = :fagsystemId
        """
    }
}
