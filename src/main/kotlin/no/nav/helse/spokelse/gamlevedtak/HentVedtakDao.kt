package no.nav.helse.spokelse.gamlevedtak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.FpVedtak
import no.nav.helse.spokelse.Utbetalingsperiode
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingObserver
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.time.measureTimedValue

internal class HentVedtakDao(private val dataSource: () -> DataSource): TbdUtbetalingObserver {

    internal companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val harDataTilOgMed = LocalDate.parse("2022-03-16")
        internal fun harData(fraOgMed: LocalDate?) = fraOgMed == null || fraOgMed <= harDataTilOgMed
        internal fun List<VedtakRow>.filtrer(fraOgMed: LocalDate?) = when (fraOgMed) {
            null -> this
            else -> filter { it.tom >= fraOgMed }
        }
    }

    data class VedtakRow(
        val fagsystemId: String,
        val utbetaltTidspunkt: LocalDateTime,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double
    )

    fun hentFpVedtak(fødselsnummer: String, fom: LocalDate?): List<FpVedtak> {
        if (!harData(fom)) return emptyList()
        return sessionOf(dataSource()).use { session ->
            @Language("PostgreSQL")
            val query = """
            (SELECT o.fagsystemid fagsystem_id,
                    u.fom         fom,
                    u.tom         tom,
                    u.grad        grad,
                    v.opprettet   utbetalt_tidspunkt
             FROM vedtak v
                      INNER JOIN oppdrag o on v.id = o.vedtak_id
                      INNER JOIN utbetaling u on o.id = u.oppdrag_id
             WHERE v.fodselsnummer = :fodselsnummer)
            UNION ALL
            (SELECT (SELECT distinct vu.utbetalingsref
                     FROM vedtak_utbetalingsref vu
                     WHERE vu.vedtaksperiode_id = ov.vedtaksperiode_id) fagsystem_id,
                    ou.fom                                              fom,
                    ou.tom                                              tom,
                    ou.grad                                             grad,
                    ov.opprettet                                        utbetalt_tidspunkt
             FROM old_vedtak ov
                      INNER JOIN old_utbetaling ou on ov.id = ou.vedtak_id
             WHERE ov.fodselsnummer = :fodselsnummer)
            ORDER BY utbetalt_tidspunkt, fom, tom
                """
            session.run(queryOf(query, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                VedtakRow(
                    fagsystemId = row.string("fagsystem_id"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom"),
                    grad = row.double("grad"),
                    utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt")
                )
            }.asList)
                .filtrer(fom)
                .groupBy { it.fagsystemId }
                .map { (_, value) ->
                    FpVedtak(
                        vedtaksreferanse = value.first().fagsystemId,
                        utbetalinger = value.map { utbetaling ->
                            Utbetalingsperiode(
                                fom = utbetaling.fom,
                                tom = utbetaling.tom,
                                grad = utbetaling.grad
                            )
                        },
                        vedtattTidspunkt = value.first().utbetaltTidspunkt
                    )
                }
        }
    }

    fun hentAnnuleringerForFødselsnummer(fødselsnummer: String): List<String> {
        @Language("PostgreSQL")
        val spørring = "SELECT fagsystem_id FROM annullering WHERE fodselsnummer = :fodselsnummer"

        return sessionOf(dataSource()).use { session ->
            session.run(queryOf(spørring, mapOf("fodselsnummer" to fødselsnummer)).map { row ->
                row.string("fagsystem_id")
            }.asList)
        }
    }

    internal fun hentSpøkelsePerioder(fødselsnummer: String, fom: LocalDate, tom: LocalDate): Set<SpøkelsePeriode> {
        if (!harData(fom)) return emptySet()
        return hentFraDb(fødselsnummer, fom, tom)
    }

    private fun hentFraDb(fødselsnummer: String, fom: LocalDate, tom: LocalDate): Set<SpøkelsePeriode> {
        val (perioder, duration) = measureTimedValue {
            @Language("PostgreSQL")
            val vedtakOppdragOgUtbetalingQuery = """
                SELECT o.fagsystemid fagsystem_id,
                    u.fom         fom,
                    u.tom         tom,
                    u.grad        grad,
                    v.opprettet   utbetalt_tidspunkt,
                    v.orgnummer   organisasjonsnummer,
                    'VedtakOppdragOgUtbetaling' tag
                FROM vedtak v
                INNER JOIN oppdrag o on v.id = o.vedtak_id
                INNER JOIN utbetaling u on o.id = u.oppdrag_id
                WHERE v.fodselsnummer = :fodselsnummer
                AND u.tom >= :fom AND NOT u.fom > :tom
            """

            @Language("PostgreSQL")
            val oldVedtakOgOldUtbetalingQuery = """
                SELECT
                    (SELECT distinct vu.utbetalingsref FROM vedtak_utbetalingsref vu WHERE vu.vedtaksperiode_id = ov.vedtaksperiode_id) fagsystem_id,
                    ou.fom fom,
                    ou.tom tom,
                    ou.grad grad,
                    ov.opprettet utbetalt_tidspunkt,
                    ov.orgnummer organisasjonsnummer,
                    'OldVedtakOgOldUtbetaling' tag
                FROM old_vedtak ov
                INNER JOIN old_utbetaling ou on ov.id = ou.vedtak_id
                WHERE ov.fodselsnummer = :fodselsnummer
                AND ou.tom >= :fom AND NOT ou.fom > :tom
            """

            @Language("PostgreSQL")
            val gamleUtbetalingerQuery = """
                SELECT
                    fagsystem_id,
                    fom,
                    tom,
                    grad,
                    opprettet utbetalt_tidspunkt,
                    orgnummer organisasjonsnummer,
                    'GamleUtbetalinger' tag
                FROM gamle_utbetalinger
                WHERE fodselsnummer = :fodselsnummer
                AND tom >= :fom AND NOT fom > :tom
            """

            @Language("PostgreSQL")
            val sammenstiltQuery = """
                $vedtakOppdragOgUtbetalingQuery UNION ALL $oldVedtakOgOldUtbetalingQuery UNION ALL $gamleUtbetalingerQuery
            """

            val annullerteFagsystemIder = hentAnnuleringerForFødselsnummer(fødselsnummer)

            sessionOf(dataSource()).use { session ->
                session.run(queryOf(sammenstiltQuery, mapOf("fodselsnummer" to fødselsnummer, "fom" to fom, "tom" to tom)).map { row ->
                    if (row.string("fagsystem_id") in annullerteFagsystemIder) null
                    else SpøkelsePeriode(
                        personidentifikator = Personidentifikator(fødselsnummer),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        grad = row.int("grad"),
                        organisasjonsnummer = row.string("organisasjonsnummer"),
                        tags = setOf("Spleis", row.string("tag"))
                    )
                }.asList).toSet()
            }
        }
        sikkerLogg.info("Oppslag mot gamle utbetalinger tok ${duration.inWholeMilliseconds}ms")
        return perioder
    }

    override fun utbetaling(meldingId: Long, utbetaling: Utbetaling) {
        // Nye utbetalinger er ikke aktuelt å lagre, det er kun annulleringer av gamle utbetalinger som er viktig å få med seg.
    }

    override fun annullering(meldingId: Long, annullering: Annullering) {
        @Language("PostgreSQL")
        val sql = "INSERT INTO alle_annulleringer (fagsystem_id) VALUES(:fagsystem_id) ON CONFLICT DO NOTHING"

        sessionOf(dataSource()).use { session ->
            listOfNotNull(annullering.arbeidsgiverFagsystemId, annullering.personFagsystemId).forEach { fagsystemId ->
                sikkerLogg.info("Lagrer annullering av fagsystemId $fagsystemId i tabellen alle_annulleringer")
                session.run(queryOf(sql, mapOf("fagsystem_id" to fagsystemId)).asExecute)
            }
        }
    }
}
