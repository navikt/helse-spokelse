package no.nav.helse.spokelse.gjenstaendesykedager

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

internal data class SisteVedtak(
    val gjenståendeSykedager: Int,
    val gjelderTilOgMed: LocalDate,
)

internal class GjenståendeSykedagerDao(
    private val dataSource: () -> DataSource,
) {
    internal fun lagreVedtak(
        meldingId: Long,
        vedtak: Vedtak,
    ) {
        sessionOf(dataSource()).use { session ->
            session.run(
                queryOf(
                    lagreVedtak,
                    mapOf(
                        "korrelasjonsId" to vedtak.korrelasjonsId,
                        "fodselsnummer" to vedtak.fødselsnummer,
                        "fom" to vedtak.fom,
                        "tom" to vedtak.tom,
                        "gjenstaendeSykedager" to vedtak.gjenståendeSykedager,
                        "sendt" to vedtak.sendt,
                        "kilde" to meldingId,
                    ),
                ).asUpdate,
            )
        }
    }

    internal fun annuller(
        meldingId: Long,
        korrelasjonsId: UUID,
        sendt: LocalDateTime,
    ) {
        sessionOf(dataSource()).use { session ->
            session.run(
                queryOf(annuller, mapOf("kilde" to meldingId, "korrelasjonsId" to korrelasjonsId, "sendt" to sendt)).asUpdate,
            )
        }
    }

    internal fun sisteVedtak(fødselsnummer: String): SisteVedtak? =
        sessionOf(dataSource()).use { session ->
            session.run(
                queryOf(sisteVedtak, mapOf("fodselsnummer" to fødselsnummer, "brukVedtakEtter" to brukVedtakEtter))
                    .map { row ->
                        SisteVedtak(
                            gjenståendeSykedager = row.int("gjenstående_sykedager"),
                            gjelderTilOgMed = row.localDate("tom"),
                        )
                    }.asSingle,
            )
        }

    private companion object {
        // Samme grense som i TbdUtbetalingApi. Vedtak før dette ligger i de gamle tabellene.
        private val brukVedtakEtter = LocalDateTime.parse("2022-03-16T21:53:18")

        // En eldre melding på samme korrelasjonsId skal ikke overskrive en nyere.
        @Language("PostgreSQL")
        private val lagreVedtak = """
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
        """

        // En annullering som er sendt før vedtaket, gjelder en tidligere versjon av vedtaket.
        @Language("PostgreSQL")
        private val annuller = """
            UPDATE gjenstående_sykedager
            SET annullert_kilde = :kilde
            WHERE korrelasjons_id = :korrelasjonsId
            AND sendt < :sendt
        """

        @Language("PostgreSQL")
        private val sisteVedtak = """
            SELECT gjenstående_sykedager, tom
            FROM gjenstående_sykedager
            WHERE fødselsnummer = :fodselsnummer
            AND annullert_kilde IS NULL
            AND sendt > :brukVedtakEtter
            ORDER BY tom DESC, sendt DESC
            LIMIT 1
        """
    }
}
