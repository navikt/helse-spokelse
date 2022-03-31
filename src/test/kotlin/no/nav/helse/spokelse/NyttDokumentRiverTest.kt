package no.nav.helse.spokelse

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.server.engine.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spokelse.Events.inntektsmeldingEvent
import no.nav.helse.spokelse.Events.sendtSøknadArbeidsgiverEvent
import no.nav.helse.spokelse.Events.sendtSøknadNavEvent
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NyttDokumentRiverTest {
    val testRapid = TestRapid()
    private lateinit var dataSource: DataSource
    private lateinit var dokumentDao: DokumentDao

    @AfterEach
    fun resetSchema() {
        PgDb.reset()
    }

    @BeforeAll
    fun setupEnv() {
        PgDb.start()

        dataSource = PgDb.connection()
        dokumentDao = DokumentDao(dataSource)

        NyttDokumentRiver(testRapid, dokumentDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skriver dokumenter til hendelse`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)

        testRapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))

        val dokumenter = dokumentDao.finnDokumenter(listOf(sykmelding.hendelseId, søknad.hendelseId, inntektsmelding.hendelseId))
        assertEquals(Dokumenter(sykmelding, søknad, inntektsmelding), dokumenter)
    }

    @Test
    fun `håndterer duplikate dokumenter`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)

        testRapid.sendTestMessage(sendtSøknadArbeidsgiverEvent(sykmelding, søknad))
        testRapid.sendTestMessage(sendtSøknadNavEvent(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingEvent(inntektsmelding))

        val dokumenter = dokumentDao.finnDokumenter(listOf(sykmelding.hendelseId, søknad.hendelseId, inntektsmelding.hendelseId))
        assertEquals(Dokumenter(sykmelding, søknad, inntektsmelding), dokumenter)
    }
}
