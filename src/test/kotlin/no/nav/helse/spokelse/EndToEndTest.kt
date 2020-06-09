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
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*
import kotlin.streams.asSequence

internal class EndToEndTest {
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
    val utbetaltDao = UtbetaltDao(dataSource)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        UtbetaltRiver(testRapid, utbetaltDao, dokumentDao)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
                DELETE FROM utbetaling;
                DELETE FROM oppdrag;
                DELETE FROM vedtak;
                DELETE FROM hendelse"""
            session.run(queryOf(query).asExecute)
        }
    }

    @Test
    fun `lagrer vedtak`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))

        testRapid.sendTestMessage(utbetalingMessage(LocalDate.of(2020, 6, 1), LocalDate.of(2020, 6, 8), 0, listOf(sykmelding, søknad, inntektsmelding)))

        val vedtak = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM vedtak"
            session.run(
                queryOf(query)
                    .map { row ->
                        Vedtak(
                            fødselsnummer = row.string("fodselsnummer"),
                            orgnummer = row.string("orgnummer"),
                            dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding),
                            oppdrag = emptyList(),
                            fom = row.localDate("fom"),
                            tom = row.localDate("tom"),
                            forbrukteSykedager = row.int("forbrukte_sykedager"),
                            gjenståendeSykedager = row.int("gjenstående_sykedager"),
                            opprettet = row.localDateTime("opprettet")
                        )
                    }.asList
            )
        }

        assertEquals(1, vedtak.size)

    }

    @Test
    fun `lagrer vedtak uten inntektsmelding`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))

        testRapid.sendTestMessage(utbetalingMessage(LocalDate.of(2020, 6, 1), LocalDate.of(2020, 6, 8), 0, listOf(sykmelding, søknad)))

        val vedtak = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM vedtak"
            session.run(
                queryOf(query)
                    .map { row ->
                        Vedtak(
                            fødselsnummer = row.string("fodselsnummer"),
                            orgnummer = row.string("orgnummer"),
                            dokumenter = Dokumenter(sykmelding, søknad, null),
                            oppdrag = emptyList(),
                            fom = row.localDate("fom"),
                            tom = row.localDate("tom"),
                            forbrukteSykedager = row.int("forbrukte_sykedager"),
                            gjenståendeSykedager = row.int("gjenstående_sykedager"),
                            opprettet = row.localDateTime("opprettet")
                        )
                    }.asList
            )
        }

        assertEquals(1, vedtak.size)

    }

    private fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${sykmelding.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

    private fun inntektsmeldingMessage(hendelse: Hendelse) =
        """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""

    @Language("JSON")
    private fun utbetalingMessage(fom: LocalDate, tom: LocalDate, tidligereBrukteSykedager: Int, hendelser: List<Hendelse>) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "fnr",
    "organisasjonsnummer": "orgnummer",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalt": [
        {
            "mottaker": "orgnummer",
            "fagområde": "SPREF",
            "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "$fom",
                    "tom": "$tom",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": ${sykedager(fom, tom)}
                }
            ]
        },
        {
            "mottaker": "fnr",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "$fom",
    "tom": "$tom",
    "forbrukteSykedager": ${tidligereBrukteSykedager + sykedager(fom, tom)},
    "gjenståendeSykedager": ${248 - tidligereBrukteSykedager - sykedager(fom, tom)},
    "opprettet": "2020-05-04T11:26:30.23846",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "e8eb9ffa-57b7-4fe0-b44c-471b2b306bb6",
    "@opprettet": "2020-05-04T11:27:13.521398",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

}
