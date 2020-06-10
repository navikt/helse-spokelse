package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.rapids_rivers.RapidApplication

@KtorExperimentalAPI
fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

@KtorExperimentalAPI
fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val dokumentDao = DokumentDao(dataSource)
    val utbetaltDao = UtbetaltDao(dataSource)
    val vedtakDao = VedtakDao(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .build().apply {
            NyttDokumentRiver(this, dokumentDao)
            UtbetaltRiver(this, utbetaltDao, dokumentDao)
            OldUtbetalingRiver(this, vedtakDao, dokumentDao)
            TilUtbetalingBehovRiver(this, dokumentDao)
            start()
        }
}

@KtorExperimentalAPI
internal fun Application.spokelse(env: Environment.Auth) {
    azureAdAppAuthentication(env)
    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(JavaTimeModule())
        }
    }
}
