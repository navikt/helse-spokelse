package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.Query
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
        val parametere =
            arrayOf(
                "meldingId" to meldingId,
                "arbeidsgiverFagsystemId" to annullering.arbeidsgiverFagsystemId,
                "personFagsystemId" to annullering.personFagsystemId,
            )
        asSQL(
            """
            DELETE FROM tbdUtbetaling_Utbetalingslinje
            WHERE fagsystemId IN (:arbeidsgiverFagsystemId, :personFagsystemId)
            """.trimIndent(),
            *parametere,
        ).update(transaksjon)
        asSQL(
            """
            UPDATE tbdUtbetaling_Utbetaling
            SET arbeidsgiverAnnuleringskilde = CASE WHEN arbeidsgiverFagsystemId = :arbeidsgiverFagsystemId THEN :meldingId ELSE arbeidsgiverAnnuleringskilde END,
                personAnnuleringskilde = CASE WHEN personFagsystemId = :personFagsystemId THEN :meldingId ELSE personAnnuleringskilde END
            WHERE arbeidsgiverFagsystemId = :arbeidsgiverFagsystemId OR personFagsystemId = :personFagsystemId
            """.trimIndent(),
            *parametere,
        ).update(transaksjon)
    }

    internal fun arbeidsgiverutbetalinger(tidsrom: Pair<Int, ChronoUnit>) =
        tell(
            asSQL(
                """
                SELECT count(1) FROM tbdUtbetaling_Utbetaling
                WHERE arbeidsgiverFagsystemId IS NOT NULL AND arbeidsgiverAnnuleringskilde IS NULL
                AND sistUtbetalt > now() - CAST(:intervall AS interval)
                """.trimIndent(),
                "intervall" to tidsrom.somIntervall(),
            ),
        )

    internal fun arbeidsgiverannulleringer(tidsrom: Pair<Int, ChronoUnit>) =
        tell(
            asSQL(
                """
                SELECT count(1) FROM tbdUtbetaling_Utbetaling
                WHERE arbeidsgiverAnnuleringskilde IS NOT NULL
                AND sistUtbetalt > now() - CAST(:intervall AS interval)
                """.trimIndent(),
                "intervall" to tidsrom.somIntervall(),
            ),
        )

    internal fun personutbetalinger(tidsrom: Pair<Int, ChronoUnit>) =
        tell(
            asSQL(
                """
                SELECT count(1) FROM tbdUtbetaling_Utbetaling
                WHERE personFagsystemId IS NOT NULL AND personAnnuleringskilde IS NULL
                AND sistUtbetalt > now() - CAST(:intervall AS interval)
                """.trimIndent(),
                "intervall" to tidsrom.somIntervall(),
            ),
        )

    internal fun personannulleringer(tidsrom: Pair<Int, ChronoUnit>) =
        tell(
            asSQL(
                """
                SELECT count(1) FROM tbdUtbetaling_Utbetaling
                WHERE personAnnuleringskilde IS NOT NULL
                AND sistUtbetalt > now() - CAST(:intervall AS interval)
                """.trimIndent(),
                "intervall" to tidsrom.somIntervall(),
            ),
        )

    private fun Pair<Int, ChronoUnit>.somIntervall(): String {
        check(first >= 1) { "Ugyldig tidsrom $first $second" }
        return "$first ${second.name.lowercase()}"
    }

    private fun tell(query: Query): Int =
        sessionOf(dataSource()).use { session ->
            query.single(session) { row -> row.int(1) } ?: 0
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
}
