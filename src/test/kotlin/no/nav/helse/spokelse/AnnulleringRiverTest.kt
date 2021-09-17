package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.commons.codec.binary.Base32
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnnulleringRiverTest {
    private val rapid = TestRapid()
    private val embeddedPostgres = setupPostgres()
    private val dataSource = testDataSource(embeddedPostgres)
    private val annulleringDao = AnnulleringDao(dataSource)

    fun fagsystemId() = Base32().encodeToString(Random.Default.nextBytes(32)).take(26)

    @BeforeAll
    fun setup() {
        AnnulleringRiver(rapid, annulleringDao)
    }

    @Test
    fun `lagrer annulleringer`() {
        val fagsystemId = fagsystemId()
        rapid.sendTestMessage(annullering("utbetaling_annullert", "fnr", "orgnummer", fagsystemId))

        assertEquals(1, hentAnnulleringerFor(fagsystemId))
    }

    @Test
    fun `lagrer annullering fra replay`() {
        val fagsystemId = fagsystemId()
        rapid.sendTestMessage(annullering("utbetaling_annullert_replay_for_pensjon", "fnr", "orgnummer", fagsystemId))

        assertEquals(1, hentAnnulleringerFor(fagsystemId))
    }

    @Test
    fun `lagrer ikke duplikat annullering fra replay`() {
        val fagsystemId = fagsystemId()
        rapid.sendTestMessage(annullering("utbetaling_annullert", "fnr", "orgnummer", fagsystemId))
        rapid.sendTestMessage(annullering("utbetaling_annullert_replay_for_pensjon", "fnr", "orgnummer", fagsystemId))

        assertEquals(1, hentAnnulleringerFor(fagsystemId))
    }

    fun hentAnnulleringerFor(fagsystemId: String) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT count(1) count FROM annullering WHERE fagsystem_id=?;"
        session.run(queryOf(query, fagsystemId).map {
            it.int("count")
        }.asSingle)!!
    }

    fun annullering(eventName: String, fødselsnummer: String, orgnummer: String, fagsystemId: String) = """
{
    "fødselsnummer": "$fødselsnummer",
    "organisasjonsnummer": "$orgnummer",
    "fagsystemId": "$fagsystemId",
    "utbetalingslinjer": [
        {
            "fom": "2020-07-01",
            "tom": "2020-07-05",
            "beløp": 1337,
            "grad": 100.0
        },
        {
            "fom": "2020-07-06",
            "tom": "2020-08-09",
            "beløp": 1337,
            "grad": 50.0
        }
    ],
    "@event_name": "$eventName"
}
    """
}
