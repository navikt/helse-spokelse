package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.Duration
import javax.sql.DataSource
import kotlin.concurrent.thread

/**
 * Fyller dagsats og totalbelop i tbdUtbetaling_Utbetalingslinje fra meldingen linjen ble lagret fra, i bolker etter id i tbdUtbetaling_Melding.
 * Hver bolk er én transaksjon som låser fremdriftsraden med SKIP LOCKED, så flere poder kan kjøre samtidig uten å gjøre samme jobb,
 * og jobben fortsetter der den slapp etter en restart. Linjer som allerede har verdiene, endres ikke.
 * Fjernes når den er ferdig i prod, og da blir kolonnene NOT NULL.
 */
internal class TbdUtbetalingslinjeBackfill(
    private val dataSource: () -> DataSource,
    private val bolkstørrelse: Int = 5_000,
    private val pauseMellomBolker: Duration = Duration.ofMillis(100),
    private val pauseNårOpptatt: Duration = Duration.ofSeconds(30),
    private val pauseEtterFeil: Duration = Duration.ofMinutes(1),
) {
    internal enum class Steg { FREMGANG, OPPTATT, FERDIG }

    internal fun startIBakgrunnen() {
        thread(isDaemon = true, name = "tbd-utbetalingslinje-backfill") { kjør() }
    }

    private fun kjør() {
        while (true) {
            val pause =
                try {
                    when (nesteBolk()) {
                        Steg.FERDIG -> return
                        Steg.OPPTATT -> pauseNårOpptatt
                        Steg.FREMGANG -> pauseMellomBolker
                    }
                } catch (e: Exception) {
                    loggError("Backfill av dagsats og totalbeløp feilet, prøver igjen om ${pauseEtterFeil.toSeconds()}s", e)
                    pauseEtterFeil
                }
            Thread.sleep(pause)
        }
    }

    internal fun nesteBolk(): Steg =
        sessionOf(dataSource()).use { session ->
            session.transaction { tx ->
                val (ferdig, sisteId) =
                    tx.run(
                        queryOf("SELECT ferdig, siste_id FROM tbd_utbetalingslinje_backfill FOR UPDATE SKIP LOCKED")
                            .map { it.boolean("ferdig") to it.long("siste_id") }
                            .asSingle,
                    ) ?: return@transaction Steg.OPPTATT
                if (ferdig) return@transaction Steg.FERDIG

                val maksId = tx.run(queryOf("SELECT COALESCE(MAX(id), 0) AS maks FROM tbdUtbetaling_Melding").map { it.long("maks") }.asSingle)!!
                if (sisteId >= maksId) {
                    tx.oppdaterFremdrift(ferdig = true, sisteId = sisteId)
                    loggInfo("Backfill av dagsats og totalbeløp er ferdig")
                    return@transaction Steg.FERDIG
                }

                val tilId = minOf(sisteId + bolkstørrelse, maksId)
                val linjer = tx.run(queryOf(FYLL_LINJER, mapOf("fra" to sisteId, "til" to tilId)).asUpdate)
                tx.oppdaterFremdrift(ferdig = false, sisteId = tilId)
                loggInfo("Backfill av dagsats og totalbeløp: til id $tilId av $maksId, $linjer linjer fylt")
                Steg.FREMGANG
            }
        }

    private fun TransactionalSession.oppdaterFremdrift(
        ferdig: Boolean,
        sisteId: Long,
    ) {
        run(queryOf("UPDATE tbd_utbetalingslinje_backfill SET ferdig = :ferdig, siste_id = :sisteId", mapOf("ferdig" to ferdig, "sisteId" to sisteId)).asUpdate)
    }

    private companion object {
        // En linje hører til meldingen i kilde, og er linjen i oppdraget med samme fagsystemId, fom, tom og grad.
        //language="PostgreSQL"
        private const val FYLL_LINJER = """
            UPDATE tbdUtbetaling_Utbetalingslinje l
            SET dagsats = CAST(linje ->> 'dagsats' AS NUMERIC), totalbelop = CAST(linje ->> 'totalbeløp' AS NUMERIC)
            FROM tbdUtbetaling_Melding t
            CROSS JOIN LATERAL (VALUES (t.melding -> 'arbeidsgiverOppdrag'), (t.melding -> 'personOppdrag')) AS o(oppdrag)
            CROSS JOIN LATERAL jsonb_array_elements(COALESCE(o.oppdrag -> 'utbetalingslinjer', CAST('[]' AS JSONB))) AS linje
            WHERE t.id > :fra AND t.id <= :til
            AND l.fagsystemId = o.oppdrag ->> 'fagsystemId'
            AND l.kilde = t.id
            AND l.fom = CAST(linje ->> 'fom' AS DATE)
            AND l.tom = CAST(linje ->> 'tom' AS DATE)
            AND l.grad = CAST(linje ->> 'grad' AS DECIMAL)
            AND l.dagsats IS NULL
            AND linje ->> 'dagsats' IS NOT NULL
            AND linje ->> 'totalbeløp' IS NOT NULL
        """
    }
}
