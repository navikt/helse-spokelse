package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.naisful.naisApp
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedager
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerDao
import no.nav.helse.spokelse.gjenstaendesykedager.gjenståendeSykedagerApi
import no.nav.helse.spokelse.grunnlag.grunnlagApi
import no.nav.helse.spokelse.infotrygd.NyInformasjonIInfotrygdPåPersonSpleisIkkeKjennerTilRiver
import no.nav.helse.spokelse.infotrygd.NyInformasjonIInfotrygdPåPersonSpleisKjennerTilRiver
import no.nav.helse.spokelse.tbdutbetaling.HelsesjekkRiver
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingConsumer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.utbetalteperioder.Azure
import no.nav.helse.spokelse.utbetalteperioder.Infotrygd
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioder
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioderRiver
import no.nav.helse.spokelse.utbetalteperioder.utbetaltePerioderApi
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.jacksonObjectMapper

internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val auth =
        Auth.auth(
            name = "ourissuer",
            clientId = env.getValue("AZURE_APP_CLIENT_ID"),
            discoveryUrl = env.getValue("AZURE_APP_WELL_KNOWN_URL"),
        )

    val dataSource = DataSourceBuilder()

    val gamleUtbetalingerDao = GamleUtbetalingerDao(dataSource::dataSource)
    val tbdUtbetalingDao = TbdUtbetalingDao(dataSource::dataSource)
    val gjenståendeSykedagerDao = GjenståendeSykedagerDao(dataSource::dataSource)

    val aivenFactory = ConsumerProducerFactory(AivenConfig.default)
    val utbetalingVarsel = UtbetalingVarsel(aivenFactory.createProducer())

    val tbdUtbetalingApi = TbdUtbetalingApi(tbdUtbetalingDao)
    val infotrygdHttpClient = HttpClient(CIO)
    val infotrygd =
        Infotrygd(
            httpClient = infotrygdHttpClient,
            scope = env.hent("INFOTRYGD_SCOPE"),
            accessToken = Azure(env, infotrygdHttpClient),
            url = env.hent("INFOTRYGD_URL"),
        )
    val utbetaltePerioder =
        UtbetaltePerioder(
            infotrygd = infotrygd,
            tbdUtbetalingApi = tbdUtbetalingApi,
            gamleUtbetalingerDao = gamleUtbetalingerDao,
        )
    val gjenståendeSykedager =
        GjenståendeSykedager(
            dao = gjenståendeSykedagerDao,
            infotrygd = infotrygd,
        )

    val tbdUtbetalingConsumer = TbdUtbetalingConsumer(env, tbdUtbetalingDao, gjenståendeSykedagerDao, observers = listOf(tbdUtbetalingDao, gamleUtbetalingerDao, utbetalingVarsel))
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    RapidApplication
        .create(
            env = env,
            meterRegistry = meterRegistry,
            builder = {
                withKtor { preStopHook, rapid ->
                    naisApp(
                        meterRegistry = meterRegistry,
                        objectMapper = jacksonObjectMapper(),
                        applicationLogger = LoggerFactory.getLogger("no.nav.helse.spokelse.App"),
                        callLogger = LoggerFactory.getLogger("no.nav.helse.spokelse.CallLogging"),
                        naisEndpoints = com.github.navikt.tbd_libs.naisful.NaisEndpoints.Default,
                        callIdHeaderName = "x-callId",
                        timersConfig = { call, _ ->
                            this
                                .tag("azp_name", call.principal<JWTPrincipal>()?.get("azp_name") ?: "n/a")
                                // https://github.com/linkerd/polixy/blob/main/DESIGN.md#l5d-client-id-client-id
                                // eksempel: <APP>.<NAMESPACE>.serviceaccount.identity.linkerd.cluster.local
                                .tag("konsument", call.request.header("L5d-Client-Id") ?: "n/a")
                        },
                        mdcEntries =
                            mapOf(
                                "azp_name" to { call: ApplicationCall -> call.principal<JWTPrincipal>()?.get("azp_name") },
                                "konsument" to { call: ApplicationCall -> call.request.header("L5d-Client-Id") },
                            ),
                        aliveCheck = rapid::isReady,
                        readyCheck = rapid::isReady,
                        preStopHook = preStopHook::handlePreStopRequest,
                    ) {
                        spokelse(auth, gamleUtbetalingerDao, tbdUtbetalingApi, utbetaltePerioder, gjenståendeSykedager)
                    }
                }
            },
        ).apply {
            registerRivers(tbdUtbetalingDao, utbetaltePerioder, utbetalingVarsel)
            register(tbdUtbetalingConsumer)
            register(
                object : RapidsConnection.StatusListener {
                    override fun onStartup(rapidsConnection: RapidsConnection) {
                        dataSource.migrate()
                    }
                },
            )
        }.start()
}

internal fun RapidsConnection.registerRivers(
    tbdUtbetalingDao: TbdUtbetalingDao,
    utbetaltePerioder: UtbetaltePerioder,
    utbetalingVarsel: UtbetalingVarsel,
) {
    HelsesjekkRiver(this, tbdUtbetalingDao)
    UtbetaltePerioderRiver(this, utbetaltePerioder)
    NyInformasjonIInfotrygdPåPersonSpleisKjennerTilRiver(this, utbetalingVarsel)
    NyInformasjonIInfotrygdPåPersonSpleisIkkeKjennerTilRiver(this, utbetalingVarsel)
}

internal fun Application.spokelse(
    auth: Auth,
    gamleUtbetalingerDao: GamleUtbetalingerDao,
    tbdUtbetalingApi: TbdUtbetalingApi,
    utbetaltePerioder: UtbetaltePerioder,
    gjenståendeSykedager: GjenståendeSykedager,
) {
    azureAdAppAuthentication(auth)
    routing {
        get("/openapi/gjenstaende-sykedager.yml") {
            call.respondText(gjenståendeSykedagerOpenApi, ContentType.parse("application/yaml"))
        }
        swaggerUI(path = "swagger", swaggerFile = "openapi/gjenstaende-sykedager.yml")
        authenticate {
            grunnlagApi(gamleUtbetalingerDao, tbdUtbetalingApi)
            utbetaltePerioderApi(utbetaltePerioder)
            gjenståendeSykedagerApi(gjenståendeSykedager)
        }
    }
}

private val gjenståendeSykedagerOpenApi =
    requireNotNull(object {}.javaClass.getResource("/openapi/gjenstaende-sykedager.yml")) { "Fant ikke OpenAPI-specen" }.readText()
