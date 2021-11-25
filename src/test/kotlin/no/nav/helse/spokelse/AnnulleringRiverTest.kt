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
        val arbeidsgiverFagsystemId = fagsystemId()
        rapid.sendTestMessage(annullering("utbetaling_annullert", "fnr", "orgnummer", arbeidsgiverFagsystemId))

        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
    }

    @Test
    fun `lagrer annullering fra replay`() {
        val arbeidsgiverFagsystemId = fagsystemId()
        rapid.sendTestMessage(annullering("utbetaling_annullert_replay_for_pensjon", "fnr", "orgnummer", arbeidsgiverFagsystemId))

        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
    }

    @Test
    fun `lagrer ikke duplikat annullering fra replay`() {
        val arbeidsgiverFagsystemId = fagsystemId()
        rapid.sendTestMessage(annullering("utbetaling_annullert", "fnr", "orgnummer", arbeidsgiverFagsystemId))
        rapid.sendTestMessage(annullering("utbetaling_annullert_replay_for_pensjon", "fnr", "orgnummer", arbeidsgiverFagsystemId))

        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
    }

    @Test
    fun `lagrer annullering for delvis refusjon hvor både person og arbeidsgiveroppdrag annulleres`() {
        val arbeidsgiverFagsystemId = fagsystemId()
        val personFagsystemId = fagsystemId()

        rapid.sendTestMessage(annullering("utbetaling_annullert", "fnr", "orgnummer", arbeidsgiverFagsystemId, personFagsystemId))
        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
        assertEquals(1, hentAnnulleringerFor(personFagsystemId, "SP"))
    }

    @Test
    fun `lagrer annullering for ingen refusjon hvor hvor kun personoppdrag annulleres`() {
        val personFagsystemId = fagsystemId()

        rapid.sendTestMessage(annullering("utbetaling_annullert", "fnr", "orgnummer", null, personFagsystemId))
        assertEquals(1, hentAnnulleringerFor(personFagsystemId, "SP"))
    }

    fun hentAnnulleringerFor(fagsystemId: String, fagområde: String) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT count(1) count FROM annullering WHERE fagsystem_id=? AND fagomrade=?;"
        session.run(queryOf(query, fagsystemId, fagområde).map {
            it.int("count")
        }.asSingle)!!
    }

    fun annullering(eventName: String, fødselsnummer: String, orgnummer: String, arbeidsgiverFagsystemId: String?, personFagsystemId: String? = null) = """
    {
        "fødselsnummer": "$fødselsnummer",
        "organisasjonsnummer": "$orgnummer",
        "arbeidsgiverFagsystemId": ${if (arbeidsgiverFagsystemId != null) "\"$arbeidsgiverFagsystemId\"" else null},
        "personFagsystemId": ${if (personFagsystemId != null) "\"$personFagsystemId\"" else null},
        "fom": "2020-07-01",
        "tom": "2020-08-09",
        "@event_name": "$eventName"
    }
    """
}
