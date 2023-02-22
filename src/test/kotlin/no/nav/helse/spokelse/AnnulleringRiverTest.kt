package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spokelse.Events.annulleringEvent
import no.nav.helse.spokelse.Events.genererFagsystemId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnnulleringRiverTest {
    private val rapid = TestRapid()
    private val fom = LocalDate.parse("2020-07-01")
    private val tom = LocalDate.parse("2020-08-09")

    private lateinit var dataSource: DataSource
    private lateinit var annulleringDao: AnnulleringDao

    @AfterEach
    fun resetSchema() {
        PgDb.reset()
        rapid.reset()
    }

    @BeforeAll
    fun setupEnv() {
        PgDb.start()

        dataSource = PgDb.connection()
        annulleringDao = AnnulleringDao(::dataSource)

        AnnulleringRiver(rapid, annulleringDao)
    }

    @Test
    fun `lagrer annulleringer`() {
        val arbeidsgiverFagsystemId = genererFagsystemId()
        rapid.sendTestMessage(annulleringEvent(
            fødselsnummer = "fnr",
            orgnummer = "orgnummer",
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            fom = fom,
            tom = tom
        ))

        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
    }

    @Test
    fun `lagrer annullering fra replay`() {
        val arbeidsgiverFagsystemId = genererFagsystemId()
        rapid.sendTestMessage(annulleringEvent(
            eventName = "utbetaling_annullert_replay_for_pensjon",
            fødselsnummer = "fnr",
            orgnummer = "orgnummer",
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            fom = fom,
            tom = tom
        ))

        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
    }

    @Test
    fun `lagrer ikke duplikat annullering fra replay`() {
        val arbeidsgiverFagsystemId = genererFagsystemId()
        rapid.sendTestMessage(annulleringEvent(
            fødselsnummer = "fnr",
            orgnummer = "orgnummer",
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            fom = fom,
            tom = tom
        ))
        rapid.sendTestMessage(annulleringEvent(
            eventName = "utbetaling_annullert_replay_for_pensjon",
            fødselsnummer = "fnr",
            orgnummer = "orgnummer",
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            fom = fom,
            tom = tom
        ))

        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
    }

    @Test
    fun `lagrer annullering for delvis refusjon hvor både person og arbeidsgiveroppdrag annulleres`() {
        val arbeidsgiverFagsystemId = genererFagsystemId()
        val personFagsystemId = genererFagsystemId()

        rapid.sendTestMessage(annulleringEvent(
            fødselsnummer = "fnr",
            orgnummer = "orgnummer",
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            personFagsystemId = personFagsystemId,
            fom = fom,
            tom = tom
        ))
        assertEquals(1, hentAnnulleringerFor(arbeidsgiverFagsystemId, "SPREF"))
        assertEquals(1, hentAnnulleringerFor(personFagsystemId, "SP"))
    }

    @Test
    fun `lagrer annullering for ingen refusjon hvor hvor kun personoppdrag annulleres`() {
        val personFagsystemId = genererFagsystemId()

        rapid.sendTestMessage(annulleringEvent(
            eventName = "utbetaling_annullert",
            fødselsnummer = "fnr",
            orgnummer = "orgnummer",
            arbeidsgiverFagsystemId = null,
            personFagsystemId = personFagsystemId,
            fom = fom,
            tom = tom
        ))
        assertEquals(1, hentAnnulleringerFor(personFagsystemId, "SP"))
    }

    fun hentAnnulleringerFor(fagsystemId: String, fagområde: String) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT count(1) count FROM annullering WHERE fagsystem_id=? AND fagomrade=?;"
        session.run(queryOf(query, fagsystemId, fagområde).map {
            it.int("count")
        }.asSingle)!!
    }
}
