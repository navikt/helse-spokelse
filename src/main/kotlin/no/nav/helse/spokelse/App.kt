package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spokelse.gamlevedtak.AnnulleringDao
import no.nav.helse.spokelse.gamlevedtak.AnnulleringRiver
import no.nav.helse.spokelse.gamlevedtak.HentVedtakDao
import no.nav.helse.spokelse.grunnlag.grunnlagApi
import no.nav.helse.spokelse.tbdutbetaling.HelsesjekkRiver
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingConsumer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbtalingApi
import no.nav.helse.spokelse.utbetalinger.utbetalingerApi
import no.nav.helse.spokelse.utbetalteperioder.utbetaltePerioderApi
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val builder = RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env))

    val auth = Auth.auth(
        name = "ourissuer",
        clientId = env.getValue("AZURE_APP_CLIENT_ID"),
        discoveryUrl = env.getValue("AZURE_APP_WELL_KNOWN_URL")
    )

    val dataSource = DataSourceBuilder()

    val vedtakDao = HentVedtakDao(dataSource::dataSource)
    val annulleringDao = AnnulleringDao(dataSource::dataSource)
    val tbdUtbetalingDao = TbdUtbetalingDao(dataSource::dataSource)

    val tbdUtbetalingConsumer = TbdUtbetalingConsumer(env, tbdUtbetalingDao)
        builder.withKtorModule { spokelse(env, auth, vedtakDao, TbdUtbtalingApi(tbdUtbetalingDao)) }
        .build(factory = ConfiguredCIO)
        .apply {
            registerRivers(annulleringDao, tbdUtbetalingDao)
            register(tbdUtbetalingConsumer)
            register(object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    dataSource.migrate()
                }
            })
        }
        .start()
}

internal fun RapidsConnection.registerRivers(
    annulleringDao: AnnulleringDao,
    tbdUtbetalingDao: TbdUtbetalingDao
) {
    AnnulleringRiver(this, annulleringDao)
    HelsesjekkRiver(this, tbdUtbetalingDao)
}

internal fun Application.spokelse(env: Map<String, String>, auth: Auth, vedtakDao: HentVedtakDao, tbdUtbtalingApi: TbdUtbtalingApi) {
    val httpClient = HttpClient(CIO)
    azureAdAppAuthentication(auth)
    requestResponseTracing(sikkerlogg)
    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(JavaTimeModule())
        }
    }
    install(CallId) {
        header("x-callId")
        verify { it.isNotEmpty() }
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        logger = sikkerlogg
        level = Level.INFO
        disableDefaultColors()
        callIdMdc("callId")
        filter { call -> setOf("/isalive", "/isready", "/metrics").none { call.request.path().contains(it) } }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            sikkerlogg.error("Feil ved håndtering av ${call.request.httpMethod.value} - ${call.request.path()}", cause)
            call.respond(InternalServerError)
        }
    }
    routing {
        authenticate {
            grunnlagApi(vedtakDao, tbdUtbtalingApi)
            utbetalingerApi(vedtakDao, tbdUtbtalingApi)
            utbetaltePerioderApi(env, httpClient, tbdUtbtalingApi, vedtakDao)
        }
    }
}

private fun ApplicationCall.applicationId() = try {
    val jwt = (authentication.principal(provider = null, JWTPrincipal::class))!!
    jwt.getClaim("azp", String::class) ?: jwt.getClaim("appid", String::class) ?: "n/a (fant ikke)"
} catch (ex: Exception) {
    sikkerlogg.error("Klarte ikke å utlede Application ID", ex)
    "n/a (feil)"
}

internal fun ApplicationCall.logRequest() = sikkerlogg.info("Mottok request mot ${request.path()} fra ${applicationId()}")
