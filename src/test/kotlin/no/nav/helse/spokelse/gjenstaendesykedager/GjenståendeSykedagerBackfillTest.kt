package no.nav.helse.spokelse.gjenstaendesykedager

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spokelse.AbstractE2ETest
import no.nav.helse.spokelse.tbdutbetaling.Melding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class GjenståendeSykedagerBackfillTest : AbstractE2ETest() {
    @Test
    fun `fyller tabellen fra råmeldingene`() {
        val revurdert = UUID.randomUUID()
        lagreRåmelding("utbetaling_utbetalt", revurdert, gjenståendeSykedager = 221, sendt = 1.februar)
        lagreRåmelding("utbetaling_utbetalt", revurdert, gjenståendeSykedager = 225, sendt = 1.mars)

        val utenUtbetaling = UUID.randomUUID()
        lagreRåmelding("utbetaling_uten_utbetaling", utenUtbetaling, tom = LocalDate.parse("2023-02-28"), gjenståendeSykedager = 0, sendt = 1.mars)

        val annullert = UUID.randomUUID()
        lagreRåmelding("utbetaling_utbetalt", annullert, tom = LocalDate.parse("2023-03-31"), gjenståendeSykedager = 200, sendt = 1.april)
        lagreRåmelding("utbetaling_annullert", annullert, sendt = 1.mai)

        val annullertFørRevurdering = UUID.randomUUID()
        lagreRåmelding("utbetaling_utbetalt", annullertFørRevurdering, tom = LocalDate.parse("2023-04-30"), gjenståendeSykedager = 190, sendt = 1.april)
        lagreRåmelding("utbetaling_annullert", annullertFørRevurdering, sendt = 1.mai)
        lagreRåmelding("utbetaling_utbetalt", annullertFørRevurdering, tom = LocalDate.parse("2023-04-30"), gjenståendeSykedager = 180, sendt = 1.juni)

        kjørBackfill()

        assertEquals(
            mapOf(
                revurdert to Rad(225, LocalDate.parse("2023-01-31"), annullert = false),
                utenUtbetaling to Rad(0, LocalDate.parse("2023-02-28"), annullert = false),
                annullert to Rad(200, LocalDate.parse("2023-03-31"), annullert = true),
                annullertFørRevurdering to Rad(180, LocalDate.parse("2023-04-30"), annullert = false),
            ),
            rader(),
        )
    }

    @Test
    fun `hopper over meldinger som mangler gjenstående sykedager`() {
        lagreRåmelding("utbetaling_utbetalt", UUID.randomUUID(), gjenståendeSykedager = null, sendt = 1.februar)
        kjørBackfill()
        assertEquals(emptyMap<UUID, Rad>(), rader())
    }

    @Test
    fun `backfill er trygg å kjøre når tabellen allerede har rader`() {
        val korrelasjonsId = UUID.randomUUID()
        lagreRåmelding("utbetaling_utbetalt", korrelasjonsId, gjenståendeSykedager = 221, sendt = 1.februar)
        kjørBackfill()
        kjørBackfill()
        assertEquals(mapOf(korrelasjonsId to Rad(221, LocalDate.parse("2023-01-31"), annullert = false)), rader())
    }

    private data class Rad(
        val gjenståendeSykedager: Int,
        val tom: LocalDate,
        val annullert: Boolean,
    )

    private fun lagreRåmelding(
        type: String,
        korrelasjonsId: UUID,
        tom: LocalDate = LocalDate.parse("2023-01-31"),
        gjenståendeSykedager: Int? = null,
        sendt: LocalDateTime,
    ) {
        val melding =
            mutableMapOf<String, Any>(
                "event" to type,
                "korrelasjonsId" to korrelasjonsId.toString(),
                "fødselsnummer" to FØDSELSNUMMER,
                "fom" to tom.withDayOfMonth(1).toString(),
                "tom" to tom.toString(),
            )
        gjenståendeSykedager?.let { melding["gjenståendeSykedager"] = it }
        tbdUtbetalingDao.lagreMelding(Melding(objectMapper.writeValueAsString(melding), sendt, type, FØDSELSNUMMER))
    }

    private fun kjørBackfill() {
        val sql = javaClass.getResource("/db/migration/V20__gjenstaende_sykedager_backfill.sql")!!.readText()
        sessionOf(dataSource).use { it.run(queryOf(sql).asExecute) }
    }

    private fun rader() =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT korrelasjons_id, gjenstående_sykedager, tom, annullert_kilde FROM gjenstående_sykedager")
                    .map { row ->
                        row.uuid("korrelasjons_id") to
                            Rad(row.int("gjenstående_sykedager"), row.localDate("tom"), row.intOrNull("annullert_kilde") != null)
                    }.asList,
            ).toMap()
        }

    private companion object {
        private const val FØDSELSNUMMER = "12345678911"
        private val objectMapper = jacksonObjectMapper()
        private val Int.februar get() = LocalDateTime.of(2023, 2, this, 12, 0)
        private val Int.mars get() = LocalDateTime.of(2023, 3, this, 12, 0)
        private val Int.april get() = LocalDateTime.of(2023, 4, this, 12, 0)
        private val Int.mai get() = LocalDateTime.of(2023, 5, this, 12, 0)
        private val Int.juni get() = LocalDateTime.of(2023, 6, this, 12, 0)
    }
}
