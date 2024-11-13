package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.grunnlag.grunnlagApi
import no.nav.helse.spokelse.tbdutbetaling.HelsesjekkRiver
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingConsumer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioder
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioderRiver
import no.nav.helse.spokelse.utbetalteperioder.utbetaltePerioderApi
import org.slf4j.LoggerFactory

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
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
    RapidApplication.create(
        env = env,
        builder = {
            withCallIdHeader("x-callId")
            withKtorModule { spokelse(env, auth, gamleUtbetalingerDao, TbdUtbetalingApi(tbdUtbetalingDao), ApplicationIdAllowlist) }
        }
    )
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
    routing {
        authenticate {
            grunnlagApi(gamleUtbetalingerDao, tbdUtbetalingApi, apiTilgangsstyring)
            utbetaltePerioderApi(UtbetaltePerioder(env, httpClient, tbdUtbetalingApi, gamleUtbetalingerDao), apiTilgangsstyring)
        }
    }
}

