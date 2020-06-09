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
    val vedtakDao = VedtakDao(dataSource)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        UtbetaltRiver(testRapid, utbetaltDao, dokumentDao)
        OldUtbetalingRiver(testRapid, vedtakDao, dokumentDao)

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
                DELETE FROM hendelse;
                DELETE FROM old_utbetaling;
                DELETE FROM old_vedtak;
                DELETE FROM vedtak_utbetalingsref;
                """
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

        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(
            utbetalingMessage(
                hendelseId,
                LocalDate.of(2020, 6, 1),
                LocalDate.of(2020, 6, 8),
                0,
                listOf(sykmelding, søknad, inntektsmelding)
            )
        )

        val vedtak = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM vedtak"
            session.run(
                queryOf(query)
                    .map { row ->
                        Vedtak(
                            hendelseId = hendelseId,
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

        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(
            utbetalingMessage(
                hendelseId,
                LocalDate.of(2020, 6, 1),
                LocalDate.of(2020, 6, 8),
                0,
                listOf(sykmelding, søknad)
            )
        )

        val vedtak = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM vedtak"
            session.run(
                queryOf(query)
                    .map { row ->
                        Vedtak(
                            hendelseId = hendelseId,
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

    @Test
    fun `rapport`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))
        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(
            utbetalingMessage(
                hendelseId,
                LocalDate.of(2020, 6, 1),
                LocalDate.of(2020, 6, 8),
                0,
                listOf(sykmelding, søknad, inntektsmelding)
            )
        )

        val rapport = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """
                        SELECT fodselsnummer, sykmelding_id, soknad_id, inntektsmelding_id,
                            (SELECT min(u.fom) FROM oppdrag o INNER JOIN utbetaling u on o.id = u.oppdrag_id WHERE o.vedtak_id = v.id) forste_utbetalingsdag,
                            (SELECT max(u.tom) FROM oppdrag o INNER JOIN utbetaling u on o.id = u.oppdrag_id WHERE o.vedtak_id = v.id) siste_utbetalingsdag,
                            (SELECT sum(o.totalbeløp) FROM oppdrag o WHERE o.vedtak_id = v.id) sum,
                            (SELECT max(u.grad) FROM oppdrag o INNER JOIN utbetaling u on o.id = u.oppdrag_id WHERE o.vedtak_id = v.id) maksgrad,
                            opprettet utbetalt_tidspunkt,
                            orgnummer,
                            forbrukte_sykedager,
                            gjenstående_sykedager,
                            fom,
                            tom
                        FROM vedtak v
                """
        }
    }

    @Test
    fun `leser vedtak på gamle versjoner`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))
        val vedtaksperiodeId = UUID.randomUUID()
        val fagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"
        testRapid.sendTestMessage(utbetalingBehov(
            vedtaksperiodeId,
            fagsystemId,
            LocalDate.of(2020, 6, 9),
            LocalDate.of(2020, 6, 20)
        ))
        testRapid.sendTestMessage(vedtakMedUtbetalingslinjernøkkel(
            LocalDate.of(2020, 6, 9),
            LocalDate.of(2020, 6, 20),
            vedtaksperiodeId,
            listOf(sykmelding, søknad, inntektsmelding))
        )

        val vedtak = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM old_vedtak"
            session.run(
                queryOf(query)
                    .map { row ->
                        OldVedtak(
                            vedtaksperiodeId = UUID.fromString(row.string("vedtaksperiode_id")),
                            fødselsnummer = row.string("fodselsnummer"),
                            orgnummer = row.string("orgnummer"),
                            dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding),
                            utbetalinger = emptyList(),
                            forbrukteSykedager = row.int("forbrukte_sykedager"),
                            gjenståendeSykedager = row.intOrNull("gjenstående_sykedager"),
                            opprettet = row.localDateTime("opprettet")
                        )
                    }.asList
            )
        }

        assertEquals(vedtaksperiodeId, vedtak.first().vedtaksperiodeId)

        val utbetalinger = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM old_utbetaling"
            session.run(
                queryOf(query)
                    .map { row ->
                        OldUtbetaling(
                            fom = row.localDate("fom"),
                            tom = row.localDate("tom"),
                            grad = row.double("grad")
                        )
                    }.asList
            )
        }


        assertEquals(1, utbetalinger.size)
        val utbetaling = utbetalinger.first()
        assertEquals(LocalDate.of(2020, 6, 9), utbetaling.fom)
        assertEquals(LocalDate.of(2020, 6, 20), utbetaling.tom)
        assertEquals(100.0, utbetaling.grad)
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
    private fun utbetalingMessage(
        hendelseId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        tidligereBrukteSykedager: Int,
        hendelser: List<Hendelse>
    ) = """{
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
    "@id": "$hendelseId",
    "@opprettet": "2020-05-04T11:27:13.521398",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""

    @Language("JSON")
    private fun vedtakMedUtbetalingslinjernøkkel(fom: LocalDate, tom: LocalDate, vedtaksperiodeId: UUID, hendelser: List<Hendelse>) = """{
    "førsteFraværsdag": "$fom",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalingslinjer": [
        {
            "fom": "$fom",
            "tom": "$tom",
            "dagsats": 1431,
            "beløp": 1431,
            "grad": 100.0,
            "enDelAvPerioden": true,
            "mottaker": "987654321",
            "konto": "SPREF"
        }
    ],
    "forbrukteSykedager": ${sykedager(fom, tom)},
    "gjenståendeSykedager": null,
    "opprettet": "2020-06-10T10:46:36.979478",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "3bcefb15-8fb0-4b9b-99d7-547c0c295820",
    "@opprettet": "2020-06-10T10:46:46.007854",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "75e4718f-ae59-4701-a09c-001630bcbd1a",
        "opprettet": "2020-06-10T10:46:37.275083"
    },
    "aktørId": "42",
    "fødselsnummer": "$FNR",
    "organisasjonsnummer": "$ORGNUMMER"
}"""

    private val FNR = "12020052345"
    private val ORGNUMMER = "987654321"

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

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

}
