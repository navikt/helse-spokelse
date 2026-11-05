package no.nav.helse.spokelse.gjenstaendesykedager

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.Duration
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.concurrent.thread

/**
 * Fyller gjenstående_sykedager fra tbdUtbetaling_Melding i bolker etter id, først utbetalinger og så annulleringer.
 * Hver bolk er én transaksjon som låser fremdriftsraden med SKIP LOCKED, så flere poder kan kjøre samtidig uten å
 * gjøre samme jobb, og jobben fortsetter der den slapp etter en restart.
 * Bolkene bruker samme regler som GjenståendeSykedagerDao, så de kan gå side om side med meldinger som kommer inn nå.
 */
internal class GjenståendeSykedagerBackfill(
    private val dataSource: () -> DataSource,
    private val bolkstørrelse: Int = 5_000,
    private val pauseMellomBolker: Duration = Duration.ofMillis(100),
    private val pauseNårOpptatt: Duration = Duration.ofSeconds(30),
    private val pauseEtterFeil: Duration = Duration.ofMinutes(1),
) {
    private enum class Fase { UTBETALINGER, ANNULLERINGER, FERDIG }

    internal enum class Steg { FREMGANG, OPPTATT, FERDIG }

    internal fun startIBakgrunnen() {
        thread(isDaemon = true, name = "gjenstaende-sykedager-backfill") { kjør() }
    }

    internal fun kjør() {
        while (true) {
            val pause =
                try {
                    when (nesteBolk()) {
                        Steg.FERDIG -> return
                        Steg.OPPTATT -> pauseNårOpptatt
                        Steg.FREMGANG -> pauseMellomBolker
                    }
                } catch (e: Exception) {
                    loggError("Backfill av gjenstående sykedager feilet, prøver igjen om ${pauseEtterFeil.toSeconds()}s", e)
                    pauseEtterFeil
                }
            Thread.sleep(pause)
        }
    }

    internal fun nesteBolk(): Steg =
        sessionOf(dataSource()).use { session ->
            session.transaction { tx ->
                val (fase, sisteId) =
                    tx.run(
                        queryOf("SELECT fase, siste_id FROM gjenstående_sykedager_backfill FOR UPDATE SKIP LOCKED")
                            .map { Fase.valueOf(it.string("fase")) to it.long("siste_id") }
                            .asSingle,
                    ) ?: return@transaction Steg.OPPTATT
                if (fase == Fase.FERDIG) return@transaction Steg.FERDIG

                val maksId = tx.run(queryOf("SELECT COALESCE(MAX(id), 0) AS maks FROM tbdUtbetaling_Melding").map { it.long("maks") }.asSingle)!!
                if (sisteId >= maksId) {
                    val nesteFase = if (fase == Fase.UTBETALINGER) Fase.ANNULLERINGER else Fase.FERDIG
                    tx.oppdaterFremdrift(nesteFase, 0)
                    loggInfo("Backfill av gjenstående sykedager: ferdig med $fase, går videre til $nesteFase")
                    return@transaction if (nesteFase == Fase.FERDIG) Steg.FERDIG else Steg.FREMGANG
                }

                val tilId = minOf(sisteId + bolkstørrelse, maksId)
                val sql = if (fase == Fase.UTBETALINGER) UTBETALINGER else ANNULLERINGER
                val rader = tx.run(queryOf(sql, mapOf("fra" to sisteId, "til" to tilId)).asUpdate)
                tx.oppdaterFremdrift(fase, tilId)
                loggInfo("Backfill av gjenstående sykedager: $fase til id $tilId av $maksId, $rader rader endret")
                Steg.FREMGANG
            }
        }

    private fun TransactionalSession.oppdaterFremdrift(
        fase: Fase,
        sisteId: Long,
    ) {
        run(queryOf("UPDATE gjenstående_sykedager_backfill SET fase = :fase, siste_id = :sisteId", mapOf("fase" to fase.name, "sisteId" to sisteId)).asUpdate)
    }

    private companion object {
        // Nyeste utbetaling vinner, også på tvers av bolker. annullert_kilde nullstilles og settes igjen i annulleringsfasen.
        // Hopper over LocalDate.MIN/MAX, som Postgres ikke kan lese som dato, slik Vedtak.harGyldigPeriode gjør i løpende lagring.
        private val UGYLDIGE_DATOER = "('${LocalDate.MIN}', '${LocalDate.MAX}')"

        //language="PostgreSQL"
        private val UTBETALINGER = """
            INSERT INTO gjenstående_sykedager(korrelasjons_id, fødselsnummer, fom, tom, gjenstående_sykedager, sendt, kilde)
            SELECT DISTINCT ON (m."korrelasjonsId") m."korrelasjonsId", t.fodselsnummer, CAST(m.fom AS DATE), CAST(m.tom AS DATE), m."gjenståendeSykedager", t.sendt, t.id
            FROM tbdUtbetaling_Melding t
            CROSS JOIN LATERAL jsonb_to_record(t.melding) AS m("korrelasjonsId" UUID, fom TEXT, tom TEXT, "gjenståendeSykedager" INTEGER)
            WHERE t.id > :fra AND t.id <= :til
            AND t.type IN ('utbetaling_utbetalt', 'utbetaling_uten_utbetaling')
            AND m."korrelasjonsId" IS NOT NULL
            AND m.fom IS NOT NULL
            AND m.tom IS NOT NULL
            AND m.fom NOT IN $UGYLDIGE_DATOER
            AND m.tom NOT IN $UGYLDIGE_DATOER
            AND m."gjenståendeSykedager" IS NOT NULL
            ORDER BY m."korrelasjonsId", t.sendt DESC, t.id DESC
            ON CONFLICT (korrelasjons_id) DO UPDATE SET
                fødselsnummer = excluded.fødselsnummer,
                fom = excluded.fom,
                tom = excluded.tom,
                gjenstående_sykedager = excluded.gjenstående_sykedager,
                sendt = excluded.sendt,
                kilde = excluded.kilde,
                annullert_kilde = NULL
            WHERE (excluded.sendt, excluded.kilde) > (gjenstående_sykedager.sendt, gjenstående_sykedager.kilde)
        """

        //language="PostgreSQL"
        private val ANNULLERINGER = """
            UPDATE gjenstående_sykedager SET annullert_kilde = annullering.id
            FROM (
                SELECT DISTINCT ON (m."korrelasjonsId") m."korrelasjonsId" AS korrelasjons_id, t.id, t.sendt
                FROM tbdUtbetaling_Melding t
                CROSS JOIN LATERAL jsonb_to_record(t.melding) AS m("korrelasjonsId" UUID)
                WHERE t.id > :fra AND t.id <= :til
                AND t.type = 'utbetaling_annullert'
                AND m."korrelasjonsId" IS NOT NULL
                ORDER BY m."korrelasjonsId", t.sendt DESC, t.id DESC
            ) AS annullering
            WHERE annullering.korrelasjons_id = gjenstående_sykedager.korrelasjons_id
            AND annullering.sendt > gjenstående_sykedager.sendt
        """
    }
}
