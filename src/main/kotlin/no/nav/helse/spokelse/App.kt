package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.grunnlag.grunnlagApi
import no.nav.helse.spokelse.tbdutbetaling.HelsesjekkRiver
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingConsumer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioder
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioderRiver
import no.nav.helse.spokelse.utbetalteperioder.utbetaltePerioderApi
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")

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

    val gamleUtbetalingerDao = GamleUtbetalingerDao(dataSource::dataSource)
    val tbdUtbetalingDao = TbdUtbetalingDao(dataSource::dataSource)

    val utbetaltePerioder = UtbetaltePerioder(env, HttpClient(CIO), TbdUtbetalingApi(tbdUtbetalingDao), gamleUtbetalingerDao)

    val tbdUtbetalingConsumer = TbdUtbetalingConsumer(env, tbdUtbetalingDao, observers = listOf(tbdUtbetalingDao, gamleUtbetalingerDao))
        builder.withKtorModule { spokelse(env, auth, gamleUtbetalingerDao, TbdUtbetalingApi(tbdUtbetalingDao), ApplicationIdAllowlist) }
        .build()
        .apply {
            registerRivers(tbdUtbetalingDao, utbetaltePerioder)
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
    tbdUtbetalingDao: TbdUtbetalingDao,
    utbetaltePerioder: UtbetaltePerioder? = null
) {
    HelsesjekkRiver(this, tbdUtbetalingDao)
    utbetaltePerioder?.let { UtbetaltePerioderRiver(this, it) }
}

internal fun Application.spokelse(env: Map<String, String>, auth: Auth, gamleUtbetalingerDao: GamleUtbetalingerDao, tbdUtbetalingApi: TbdUtbetalingApi, apiTilgangsstyring: ApiTilgangsstyring) {
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
            grunnlagApi(gamleUtbetalingerDao, tbdUtbetalingApi, apiTilgangsstyring)
            utbetaltePerioderApi(UtbetaltePerioder(env, httpClient, tbdUtbetalingApi, gamleUtbetalingerDao), apiTilgangsstyring)
        }
    }
}

