package no.nav.helse.spokelse

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.rapids_rivers.inMemoryRapid
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {
    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var hikariConfig: HikariConfig
    private lateinit var dataSource: HikariDataSource
    private lateinit var vedtakDAO: VedtakDAO
    private val rapid = inMemoryRapid {}

    @BeforeAll
    fun setup() {
        embeddedPostgres = EmbeddedPostgres.builder().start()

        hikariConfig = HikariConfig().apply {
            this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }

        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate()

        vedtakDAO = VedtakDAO(dataSource)

        VedtakRiver(rapid, vedtakDAO)
    }

    @Test
    fun `skriver vedtak til db`(){
        rapid.sendToListeners("""{"id":3, "fnr":"01010145678"}""")

        assertEquals(3, vedtakDAO.hentVedtak("01010145678")?.id)
    }
}
