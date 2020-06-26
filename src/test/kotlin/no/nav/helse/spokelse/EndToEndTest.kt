package no.nav.helse.spokelse

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.Row
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
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.streams.asSequence

private const val FNR = "12020052345"
private const val ORGNUMMER = "987654321"

internal class EndToEndTest {
    val testRapid = TestRapid()
    val dataSource = testDataSource()
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
    fun `genererer rapport fra alle vedtak i basen`() {
        fun Row.uuid(columnLabel: String): UUID = UUID.fromString(string(columnLabel))

        data class Rapport(
            val fødselsnummer: String,
            val sykmeldingId: UUID,
            val soknadId: UUID,
            val inntektsmeldingId: UUID,
            val førsteUtbetalingsdag: LocalDate,
            val sisteUtbetalingsdag: LocalDate,
            val sum: Int,
            val maksgrad: Double,
            val utbetaltTidspunkt: LocalDateTime,
            val orgnummer: String,
            val forbrukteSykedager: Int,
            val gjenståendeSykedager: Int?,
            val fom: LocalDate,
            val tom: LocalDate
        )

        val gammeltVedtakSøknadHendelseId = UUID.randomUUID()
        val gammeltVedtakSykmelding = Hendelse(UUID.randomUUID(), gammeltVedtakSøknadHendelseId, Dokument.Sykmelding)
        val gammeltVedtakSøknad = Hendelse(UUID.randomUUID(), gammeltVedtakSøknadHendelseId, Dokument.Søknad)
        val gammeltVedtakInntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(gammeltVedtakSykmelding, gammeltVedtakSøknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(gammeltVedtakInntektsmelding))
        val gammeltVedtakVedtaksperiodeId = UUID.randomUUID()
        val gammeltVedtakFagsystemId = "VNDG2PFPMNB4FKMC4ORASZ2JJ4"
        testRapid.sendTestMessage(
            utbetalingBehov(
                gammeltVedtakVedtaksperiodeId,
                gammeltVedtakFagsystemId,
                LocalDate.of(2020, 6, 9),
                LocalDate.of(2020, 6, 20)
            )
        )
        testRapid.sendTestMessage(
            vedtakMedUtbetalingslinjernøkkel(
                LocalDate.of(2020, 6, 9),
                LocalDate.of(2020, 6, 20),
                gammeltVedtakVedtaksperiodeId,
                listOf(gammeltVedtakSykmelding, gammeltVedtakSøknad, gammeltVedtakInntektsmelding)
            )
        )

        val nyttVedtakSøknadHendelseId = UUID.randomUUID()
        val nyttVedtakSykmelding = Hendelse(UUID.randomUUID(), nyttVedtakSøknadHendelseId, Dokument.Sykmelding)
        val nyttVedtakSøknad = Hendelse(UUID.randomUUID(), nyttVedtakSøknadHendelseId, Dokument.Søknad)
        val nyttVedtakInntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        testRapid.sendTestMessage(sendtSøknadMessage(nyttVedtakSykmelding, nyttVedtakSøknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(nyttVedtakInntektsmelding))
        val nyttVedtakHendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(
            utbetalingMessage(
                nyttVedtakHendelseId,
                LocalDate.of(2020, 7, 1),
                LocalDate.of(2020, 7, 8),
                0,
                listOf(nyttVedtakSykmelding, nyttVedtakSøknad, nyttVedtakInntektsmelding)
            )
        )

        val rapport = sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = """(SELECT v.fodselsnummer,
                                v.sykmelding_id,
                                v.soknad_id,
                                v.inntektsmelding_id,
                                o.totalbeløp            sum,
                                o.fagsystemid,
                                u.fom                   forste_utbetalingsdag,
                                u.tom                   siste_utbetalingsdag,
                                u.grad                  maksgrad,
                                u.belop,
                                u.dagsats,
                                u.sykedager,
                                (u.belop * u.sykedager) totalbelop,
                                v.opprettet             utbetalt_tidspunkt,
                                v.orgnummer,
                                v.forbrukte_sykedager,
                                v.gjenstående_sykedager,
                                v.fom,
                                v.tom
                         FROM vedtak v
                                  INNER JOIN oppdrag o on v.id = o.vedtak_id
                                  INNER JOIN utbetaling u on o.id = u.oppdrag_id)
                        UNION ALL
                        (SELECT ov.fodselsnummer,
                                ov.sykmelding_id,
                                ov.soknad_id,
                                ov.inntektsmelding_id,
                                (SELECT sum(ou3.totalbelop) FROM old_utbetaling ou3 WHERE ou3.vedtak_id = ov.id) sum,
                                (SELECT distinct vu.utbetalingsref
                                 FROM vedtak_utbetalingsref vu
                                 WHERE vu.vedtaksperiode_id = ov.vedtaksperiode_id)                              fagsystemId,
                                ou2.fom                                                                          forste_utbetalingsdag,
                                ou2.tom                                                                          siste_utbetalingsdag,
                                ou2.grad                                                                         maksgrad,
                                ou2.belop,
                                ou2.dagsats,
                                (ou2.totalbelop / ou2.belop)                                                     sykedager,
                                ou2.totalbelop,
                                ov.opprettet                                                                     utbetalt_tidspunkt,
                                ov.orgnummer,
                                ov.forbrukte_sykedager,
                                ov.gjenstående_sykedager,
                                ou2.fom                                                                          fom,
                                ou2.tom                                                                          tom
                         FROM old_vedtak ov
                                  INNER JOIN old_utbetaling ou2 on ov.id = ou2.vedtak_id)
                        ORDER BY utbetalt_tidspunkt, forste_utbetalingsdag, siste_utbetalingsdag
                """
            session.run(queryOf(query).map { row ->
                Rapport(
                    fødselsnummer = row.string("fodselsnummer"),
                    sykmeldingId = row.uuid("sykmelding_id"),
                    soknadId = row.uuid("soknad_id"),
                    inntektsmeldingId = row.uuid("inntektsmelding_id"),
                    førsteUtbetalingsdag = row.localDate("forste_utbetalingsdag"),
                    sisteUtbetalingsdag = row.localDate("siste_utbetalingsdag"),
                    sum = row.int("sum"),
                    maksgrad = row.double("maksgrad"),
                    utbetaltTidspunkt = row.localDateTime("utbetalt_tidspunkt"),
                    orgnummer = row.string("orgnummer"),
                    forbrukteSykedager = row.int("forbrukte_sykedager"),
                    gjenståendeSykedager = row.intOrNull("gjenstående_sykedager"),
                    fom = row.localDate("fom"),
                    tom = row.localDate("tom")
                )
            }.asList)
        }.sortedBy { it.fom }

        assertEquals(2, rapport.size)
        assertEquals(
            Rapport(
                fødselsnummer = FNR,
                sykmeldingId = gammeltVedtakSykmelding.dokumentId,
                soknadId = gammeltVedtakSøknad.dokumentId,
                inntektsmeldingId = gammeltVedtakInntektsmelding.dokumentId,
                førsteUtbetalingsdag = LocalDate.of(2020, 6, 9),
                sisteUtbetalingsdag = LocalDate.of(2020, 6, 20),
                sum = 12879,
                maksgrad = 100.0,
                utbetaltTidspunkt = LocalDateTime.of(2020, 6, 10, 10, 46, 46, 7000000),
                orgnummer = ORGNUMMER,
                forbrukteSykedager = 9,
                gjenståendeSykedager = null,
                fom = LocalDate.of(2020, 6, 9),
                tom = LocalDate.of(2020, 6, 20)
            ),
            rapport.first()
        )

        assertEquals(
            Rapport(
                fødselsnummer = FNR,
                sykmeldingId = nyttVedtakSykmelding.dokumentId,
                soknadId = nyttVedtakSøknad.dokumentId,
                inntektsmeldingId = nyttVedtakInntektsmelding.dokumentId,
                førsteUtbetalingsdag = LocalDate.of(2020, 7, 1),
                sisteUtbetalingsdag = LocalDate.of(2020, 7, 8),
                sum = 8586,
                maksgrad = 100.0,
                utbetaltTidspunkt = LocalDateTime.of(2020, 5, 4, 11, 27, 13, 521000000),
                orgnummer = ORGNUMMER,
                forbrukteSykedager = 6,
                gjenståendeSykedager = 242,
                fom = LocalDate.of(2020, 7, 1),
                tom = LocalDate.of(2020, 7, 8)
            ),
            rapport.last()
        )

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
        testRapid.sendTestMessage(
            utbetalingBehov(
                vedtaksperiodeId,
                fagsystemId,
                LocalDate.of(2020, 6, 9),
                LocalDate.of(2020, 6, 20)
            )
        )
        testRapid.sendTestMessage(
            vedtakMedUtbetalingslinjernøkkel(
                LocalDate.of(2020, 6, 9),
                LocalDate.of(2020, 6, 20),
                vedtaksperiodeId,
                listOf(sykmelding, søknad, inntektsmelding)
            )
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
                            grad = row.double("grad"),
                            dagsats = row.int("dagsats"),
                            beløp = row.int("belop"),
                            totalbeløp = row.int("totalbelop")
                        )
                    }.asList
            )
        }


        assertEquals(1, utbetalinger.size)
        val utbetaling = utbetalinger.first()
        assertEquals(LocalDate.of(2020, 6, 9), utbetaling.fom)
        assertEquals(LocalDate.of(2020, 6, 20), utbetaling.tom)
        assertEquals(100.0, utbetaling.grad)
        assertEquals(1431, utbetaling.dagsats)
        assertEquals(1431, utbetaling.beløp)
        assertEquals(12879, utbetaling.totalbeløp)
    }


    @Language("JSON")
    private fun utbetalingMessage(
        hendelseId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        tidligereBrukteSykedager: Int,
        hendelser: List<Hendelse>
    ) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "$FNR",
    "organisasjonsnummer": "$ORGNUMMER",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalt": [
        {
            "mottaker": "$ORGNUMMER",
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
            "mottaker": "$FNR",
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
    private fun vedtakMedUtbetalingslinjernøkkel(
        fom: LocalDate,
        tom: LocalDate,
        vedtaksperiodeId: UUID,
        hendelser: List<Hendelse>
    ) = """{
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
    "organisasjonsnummer": "$ORGNUMMER",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "tilstand": "TIL_UTBETALING",
    "mottaker": "$ORGNUMMER",
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

fun testDataSource(): DataSource {
    val embeddedPostgres = EmbeddedPostgres.builder().setPort(56789).start()
    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
    return HikariDataSource(hikariConfig)
        .apply {
            Flyway
                .configure()
                .dataSource(this)
                .load().also { it.migrate() }
        }

}

fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
    """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

fun inntektsmeldingMessage(hendelse: Hendelse) =
    """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""
