package no.nav.helse.spokelse

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.asSequence

class VedtakV2RiverTest {

    val testRapid = TestRapid()
    val embeddedPostgres = EmbeddedPostgres.builder().start()
    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
    val dataSource = HikariDataSource(hikariConfig)

    init {
        VedtakV2River(testRapid)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

//    @Test
//    fun `leser inn utbetaling og lagrer i basen`() {
//        testRapid.sendTestMessage(vedtakV3(
//            fom = LocalDate.of(2020, 6, 5),
//            tom = LocalDate.of(2020, 6, 31),
//            tidligereBrukteSykedager = 0
//        ))
//
//        assertEquals(1, vedtakDAO.hentVedtakListe("fnr").size)
//    }

}

@Language("JSON")
private fun vedtakV3(fom: LocalDate, tom: LocalDate, tidligereBrukteSykedager: Int) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "fnr",
    "organisasjonsnummer": "orgnummer",
    "hendelser": [
        "7c1a1edb-60b9-4a1f-b976-ef39d4d5021c",
        "798f60a1-6f6f-4d07-a036-1f89bd36baca",
        "ee8bc585-e898-4f4c-8662-f2a9b394896e"
    ],
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
