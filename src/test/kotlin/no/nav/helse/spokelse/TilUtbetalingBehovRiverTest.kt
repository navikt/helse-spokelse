package no.nav.helse.spokelse

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class TilUtbetalingBehovRiverTest {
    val testRapid = TestRapid()
    val embeddedPostgres = EmbeddedPostgres.builder().setPort(56789).start()
    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
    val dataSource = HikariDataSource(hikariConfig)
    val dokumentDao = DokumentDao(dataSource)

    init {
        TilUtbetalingBehovRiver(testRapid, dokumentDao)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skriver dokumenter til hendelse`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"

        testRapid.sendTestMessage(utbetalingBehov(
            vedtaksperiodeId,
            fagsystemId,
            LocalDate.of(2020, 6, 9),
            LocalDate.of(2020, 6, 20)
        ))

        val refs = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """SELECT utbetalingsref
            FROM vedtak_utbetalingsref
            WHERE vedtaksperiode_id = ?"""
            session.run(
                queryOf(
                    query,
                    vedtaksperiodeId
                ).map { row ->
                    row.string("utbetalingsref")
                }.asList
            )
        }

        assertEquals(1, refs.size)
        assertEquals(fagsystemId, refs.first())
    }

    @Language("JSON")
    private fun utbetalingBehov(vedtaksperiodeId: UUID, fagsystemId: String, fom: LocalDate, tom: LocalDate) = """{
    "@event_name": "behov",
    "@opprettet": "2020-06-10T10:02:21.069247",
    "@id": "65d0df95-2b8f-4ac7-8d73-e0b41f575330",
    "@behov": [
        "Utbetaling"
    ],
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "7eb871b1-8a40-49a6-8f9d-c9da3e3c6d73",
        "opprettet": "2020-06-10T09:59:45.873566"
    },
    "aktørId": "42",
    "fødselsnummer": "12020052345",
    "organisasjonsnummer": "987654321",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "tilstand": "TIL_UTBETALING",
    "mottaker": "987654321",
    "fagområde": "SPREF",
    "linjer": [
        {
            "fom": "$fom",
            "tom": "$tom",
            "dagsats": 1431,
            "lønn": 1431,
            "grad": 100.0,
            "refFagsystemId": null,
            "delytelseId": 1,
            "datoStatusFom": null,
            "statuskode": null,
            "refDelytelseId": null,
            "endringskode": "NY",
            "klassekode": "SPREFAG-IOP"
        }
    ],
    "fagsystemId": "$fagsystemId",
    "endringskode": "NY",
    "sisteArbeidsgiverdag": null,
    "nettoBeløp": 8586,
    "saksbehandler": "en_saksbehandler",
    "maksdato": "2019-01-01",
    "system_read_count": 0
}
"""
}
