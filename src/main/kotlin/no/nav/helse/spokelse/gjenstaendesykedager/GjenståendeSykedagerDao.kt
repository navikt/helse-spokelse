package no.nav.helse.spokelse.gjenstaendesykedager

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.single
import no.nav.helse.spokelse.felles.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

internal data class SisteVedtak(
    val gjenståendeDager: Int,
    val vurdertTilOgMed: LocalDate,
)

internal class GjenståendeSykedagerDao(
    private val dataSource: () -> DataSource,
) {
    internal fun lagreVedtak(
        transaksjon: TransactionalSession,
        meldingId: Long,
        vedtak: Vedtak,
    ) {
        asSQL(
            """
            INSERT INTO gjenstående_sykedager(korrelasjons_id, fødselsnummer, fom, tom, gjenstående_sykedager, sendt, kilde)
            VALUES (:korrelasjonsId, :fodselsnummer, :fom, :tom, :gjenstaendeSykedager, :sendt, :kilde)
            ON CONFLICT (korrelasjons_id) DO UPDATE SET
                fødselsnummer = excluded.fødselsnummer,
                fom = excluded.fom,
                tom = excluded.tom,
                gjenstående_sykedager = excluded.gjenstående_sykedager,
                sendt = excluded.sendt,
                kilde = excluded.kilde,
                annullert_kilde = NULL
            WHERE excluded.sendt >= gjenstående_sykedager.sendt
            """.trimIndent(),
            "korrelasjonsId" to vedtak.korrelasjonsId,
            "fodselsnummer" to vedtak.fødselsnummer,
            "fom" to vedtak.fom,
            "tom" to vedtak.tom,
            "gjenstaendeSykedager" to vedtak.gjenståendeSykedager,
            "sendt" to vedtak.sendt,
            "kilde" to meldingId,
        ).update(transaksjon)
    }

    internal fun annuller(
        transaksjon: TransactionalSession,
        meldingId: Long,
        korrelasjonsId: UUID,
        sendt: LocalDateTime,
    ) {
        asSQL(
            """
            UPDATE gjenstående_sykedager
            SET annullert_kilde = :kilde
            WHERE korrelasjons_id = :korrelasjonsId
            AND sendt < :sendt
            """.trimIndent(),
            "kilde" to meldingId,
            "korrelasjonsId" to korrelasjonsId,
            "sendt" to sendt,
        ).update(transaksjon)
    }

    internal fun sisteVedtak(personidentifikator: Personidentifikator): SisteVedtak? =
        sessionOf(dataSource()).use { session ->
            asSQL(
                """
                SELECT gjenstående_sykedager, tom
                FROM gjenstående_sykedager
                WHERE fødselsnummer = :fodselsnummer
                AND annullert_kilde IS NULL
                AND sendt > :brukVedtakEtter
                ORDER BY tom DESC, sendt DESC
                LIMIT 1
                """.trimIndent(),
                "fodselsnummer" to personidentifikator.toString(),
                "brukVedtakEtter" to brukVedtakEtter,
            ).single(session) { row ->
                SisteVedtak(
                    gjenståendeDager = row.int("gjenstående_sykedager"),
                    vurdertTilOgMed = row.localDate("tom"),
                )
            }
        }

    private companion object {
        // Samme grense som i TbdUtbetalingApi. Vedtak før dette ligger i de gamle tabellene.
        private val brukVedtakEtter = LocalDateTime.parse("2022-03-16T21:53:18")
    }
}
