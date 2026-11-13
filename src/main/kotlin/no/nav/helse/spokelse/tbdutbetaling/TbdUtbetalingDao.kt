package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.list
import no.nav.helse.spokelse.felles.single
import no.nav.helse.spokelse.felles.update
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

internal class TbdUtbetalingDao(
    private val dataSource: () -> DataSource,
) {
    internal fun lagreMelding(
        transaksjon: TransactionalSession,
        melding: Melding,
    ): Long =
        requireNotNull(
            asSQL(
                """
                INSERT INTO tbdUtbetaling_Melding(melding, sendt, type, fodselsnummer)
                VALUES (:melding::json, :sendt, :type, :fodselsnummer)
                RETURNING id
                """.trimIndent(),
                "melding" to "$melding",
                "sendt" to melding.meldingSendt,
                "type" to melding.type,
                "fodselsnummer" to melding.fødselsnummer,
            ).single(transaksjon) { it.long("id") },
        ) {
            "Klart ikke å lagre melding"
        }

    internal fun lagreUtbetaling(
        transaksjon: TransactionalSession,
        meldingId: Long,
        utbetaling: Utbetaling,
    ) {
        // Sletter eventuell eksisterende utbetaling (og tilhørende utbetalingslinjer)
        asSQL(
            "DELETE FROM tbdUtbetaling_Utbetaling WHERE korrelasjonsId = :korrelasjonsId",
            "korrelasjonsId" to utbetaling.korrelasjonsId,
        ).update(transaksjon)

        asSQL(
            """
            INSERT INTO tbdUtbetaling_Utbetaling(kilde, gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, fodselsnummer, korrelasjonsId, sistUtbetalt, organisasjonsnummer)
            VALUES (:meldingId, :gjenstaaendeSykedager, :arbeidsgiverFagsystemId, :personFagsystemId, :fodselsnummer, :korrelasjonsId, :sistUtbetalt, :organisasjonsnummer)
            """.trimIndent(),
            "meldingId" to meldingId,
            "gjenstaaendeSykedager" to utbetaling.gjenståendeSykedager,
            "arbeidsgiverFagsystemId" to utbetaling.arbeidsgiverOppdrag?.fagsystemId,
            "personFagsystemId" to utbetaling.personOppdrag?.fagsystemId,
            "fodselsnummer" to utbetaling.fødselsnummer,
            "korrelasjonsId" to utbetaling.korrelasjonsId,
            "sistUtbetalt" to utbetaling.sistUtbetalt,
            "organisasjonsnummer" to utbetaling.organisasjonsnummer,
        ).update(transaksjon)

        listOfNotNull(utbetaling.arbeidsgiverOppdrag, utbetaling.personOppdrag).forEach { oppdrag ->
            oppdrag.utbetalingslinjer.forEach { utbetalingslinje ->
                asSQL(
                    """
                    INSERT INTO tbdUtbetaling_Utbetalingslinje(kilde, fagsystemId, fom, tom, grad, utbetaling)
                    VALUES (:meldingId, :fagsystemId, :fom, :tom, :grad, :utbetaling)
                    """.trimIndent(),
                    "meldingId" to meldingId,
                    "fagsystemId" to oppdrag.fagsystemId,
                    "fom" to utbetalingslinje.fom,
                    "tom" to utbetalingslinje.tom,
                    "grad" to utbetalingslinje.grad,
                    "utbetaling" to utbetaling.korrelasjonsId,
                ).update(transaksjon)
            }
        }
    }

    internal fun hentUtbetalinger(
        fødselsnummer: String,
        fom: LocalDate? = null,
        tom: LocalDate? = null,
    ): List<Utbetaling> =
        sessionOf(dataSource()).use { session ->
            asSQL(
                """
                SELECT gjenstaaendeSykedager, arbeidsgiverFagsystemId, personFagsystemId, korrelasjonsId, sistUtbetalt, organisasjonsnummer
                FROM tbdUtbetaling_Utbetaling
                WHERE fodselsnummer = :fodselsnummer
                AND (arbeidsgiverAnnuleringskilde IS NULL OR personAnnuleringskilde IS NULL)
                """.trimIndent(),
                "fodselsnummer" to fødselsnummer,
            ).list(session) { row ->
                val arbeidsgiverOppdrag = row.stringOrNull("arbeidsgiverFagsystemId")?.let { session.hentOppdrag(it, fom, tom) }
                val personOppdrag = row.stringOrNull("personFagsystemId")?.let { session.hentOppdrag(it, fom, tom) }
                if (arbeidsgiverOppdrag == null && personOppdrag == null) return@list null
                Utbetaling(
                    fødselsnummer = fødselsnummer,
                    korrelasjonsId = row.uuid("korrelasjonsId"),
                    gjenståendeSykedager = row.int("gjenstaaendeSykedager"),
                    arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                    personOppdrag = personOppdrag,
                    sistUtbetalt = row.localDateTime("sistUtbetalt"),
                    organisasjonsnummer = row.stringOrNull("organisasjonsnummer"),
                )
            }
        }

    internal fun annuller(
        transaksjon: TransactionalSession,
        meldingId: Long,
        annullering: Annullering,
    ) {
        annullering.arbeidsgiverFagsystemId?.let { fagsystemId ->
            transaksjon.slettUtbetalingslinjer(fagsystemId)
            asSQL(
                """
                UPDATE tbdUtbetaling_Utbetaling
                SET arbeidsgiverAnnuleringskilde = :meldingId
                WHERE arbeidsgiverFagsystemId = :fagsystemId
                """.trimIndent(),
                "meldingId" to meldingId,
                "fagsystemId" to fagsystemId,
            ).update(transaksjon)
        }
        annullering.personFagsystemId?.let { fagsystemId ->
            transaksjon.slettUtbetalingslinjer(fagsystemId)
            asSQL(
                """
                UPDATE tbdUtbetaling_Utbetaling
                SET personAnnuleringskilde = :meldingId
                WHERE personFagsystemId = :fagsystemId
                """.trimIndent(),
                "meldingId" to meldingId,
                "fagsystemId" to fagsystemId,
            ).update(transaksjon)
        }
    }

    internal fun arbeidsgiverutbetalinger(tidsrom: Pair<Int, ChronoUnit>) = helsesjekk("arbeidsgiverfagsystemid IS NOT NULL AND arbeidsgiverannuleringskilde IS NULL", tidsrom)

    internal fun arbeidsgiverannulleringer(tidsrom: Pair<Int, ChronoUnit>) = helsesjekk("arbeidsgiverannuleringskilde IS NOT NULL", tidsrom)

    internal fun personutbetalinger(tidsrom: Pair<Int, ChronoUnit>) = helsesjekk("personfagsystemid IS NOT NULL AND personannuleringskilde IS NULL", tidsrom)

    internal fun personannulleringer(tidsrom: Pair<Int, ChronoUnit>) = helsesjekk("personannuleringskilde IS NOT NULL", tidsrom)

    private fun helsesjekk(
        where: String,
        tidsrom: Pair<Int, ChronoUnit>,
    ): Int {
        check(tidsrom.first >= 1) { "Ugyldig tidsrom ${tidsrom.first} ${tidsrom.second}" }
        return sessionOf(dataSource()).use { session ->
            asSQL(
                "SELECT count(1) FROM tbdutbetaling_utbetaling WHERE $where AND sistutbetalt > now() - INTERVAL '${tidsrom.first} ${tidsrom.second}'",
            ).single(session) { row -> row.int(1) } ?: 0
        }
    }

    private fun Session.hentOppdrag(
        fagsystemId: String,
        fom: LocalDate?,
        tom: LocalDate?,
    ): Oppdrag? =
        asSQL(
            """
            SELECT fom, tom, grad
            FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId = :fagsystemId
            AND (CAST(:fom AS date) IS NULL OR tom >= :fom)
            AND (CAST(:tom AS date) IS NULL OR NOT fom > :tom)
            """.trimIndent(),
            "fagsystemId" to fagsystemId,
            "fom" to fom,
            "tom" to tom,
        ).list(this) { row ->
            Utbetalingslinje(
                fom = row.localDate("fom"),
                tom = row.localDate("tom"),
                grad = row.double("grad"),
            )
        }.takeUnless { it.isEmpty() }
            ?.let { Oppdrag(fagsystemId, it) }

    private fun TransactionalSession.slettUtbetalingslinjer(fagsystemId: String) {
        asSQL(
            "DELETE FROM tbdUtbetaling_Utbetalingslinje WHERE fagsystemId = :fagsystemId",
            "fagsystemId" to fagsystemId,
        ).update(this)
    }
}
