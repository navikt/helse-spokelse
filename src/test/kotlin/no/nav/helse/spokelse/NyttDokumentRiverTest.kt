package no.nav.helse.spokelse

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spokelse.Events.inntektsmeldingEvent
import no.nav.helse.spokelse.Events.sendtSøknadArbeidsgiverEvent
import no.nav.helse.spokelse.Events.sendtSøknadNavEvent
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class NyttDokumentRiverTest {
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
        NyttDokumentRiver(testRapid, dokumentDao)

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
