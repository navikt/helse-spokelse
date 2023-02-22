package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingConsumer
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbtalingApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")

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
        builder.withKtorModule { spokelse(auth, vedtakDao, TbdUtbtalingApi(tbdUtbetalingDao)) }
        .build(factory = ConfiguredCIO)
        .apply {
            registerRivers(annulleringDao)
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
    annulleringDao: AnnulleringDao
) {
    AnnulleringRiver(this, annulleringDao)
}

internal fun Application.spokelse(env: Auth, vedtakDao: HentVedtakDao, tbdUtbtalingApi: TbdUtbtalingApi) {
    azureAdAppAuthentication(env)
    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(JavaTimeModule())
        }
    }
    routing {
        authenticate {
            grunnlagApi(vedtakDao, tbdUtbtalingApi)
            utbetalingerApi(vedtakDao, tbdUtbtalingApi)
        }
    }
}

internal fun Route.grunnlagApi(vedtakDAO: HentVedtakDao, tbdUtbtalingApi: TbdUtbtalingApi) {
    get("/grunnlag") {
        call.logRequest()
        val fødselsnummer = call.request.queryParameters["fodselsnummer"]
            ?: run {
                log.error("/grunnlag Mangler fodselsnummer query param")
                return@get call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer query param")
            }
        val fom = call.request.queryParameters["fom"]?.let {
            it.asLocalDateOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Ugyldig fom query param")
        }
        val time = measureTimeMillis {
            try {
                val vedtak = vedtakDAO.hentVedtakListe(fødselsnummer, fom) + tbdUtbtalingApi.hentFpVedtak(fødselsnummer, fom)
                call.respond(HttpStatusCode.OK, vedtak)
            } catch (e: Exception) {
                log.error("Feil ved henting av vedtak", e)
                tjenestekallLog.error("Feil ved henting av vedtak for $fødselsnummer", e)
                call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
            }
        }
        tjenestekallLog.info("FP hentet vedtak for $fødselsnummer fra og med $fom ($time ms)")
    }
}

internal fun Route.utbetalingerApi(vedtakDAO: HentVedtakDao, tbdUtbtalingApi: TbdUtbtalingApi) {
    post("/utbetalinger") {
        call.logRequest()
        val fødselsnumre = call.receive<List<String>>()
        val utbetalinger = fødselsnumre.flatMap { fødselsnummer ->
            val annulerteFagsystemIder = vedtakDAO.hentAnnuleringerForFødselsnummer(fødselsnummer)

            vedtakDAO.hentUtbetalingerForFødselsnummer(fødselsnummer)
                .filterNot { it.fagsystemId in annulerteFagsystemIder }
                .map {
                    UtbetalingDTO(
                        fødselsnummer = fødselsnummer,
                        fom = it.fom,
                        tom = it.tom,
                        grad = it.grad,
                        utbetaltTidspunkt = it.utbetaltTidspunkt,
                        gjenståendeSykedager = it.gjenståendeSykedager,
                        refusjonstype = it.refusjonstype
                    )
                }.ifEmpty {
                    listOf(
                        UtbetalingDTO(
                            fødselsnummer = fødselsnummer,
                            fom = null,
                            tom = null,
                            grad = 0.0,
                            gjenståendeSykedager = null,
                            utbetaltTidspunkt = null,
                            refusjonstype = null
                        )
                    )
                }
        } + tbdUtbtalingApi.hentUtbetalingDTO(fødselsnumre)
        tjenestekallLog.info("Spokelse ble bedt om informasjon om ${fødselsnumre.size} fnr, og fant informasjon ekte om ${utbetalinger.unikeFnrMedEkteUtbetalinger().size} fnr")
        call.respond(utbetalinger)
    }
}

private fun List<UtbetalingDTO>.unikeFnrMedEkteUtbetalinger() = this.filter {
    it != UtbetalingDTO(fødselsnummer = it.fødselsnummer, null, null, 0.0, null, null, null)
}.map { it.fødselsnummer }
    .toSet()

private fun ApplicationCall.applicationId() = try {
    val jwt = (authentication.principal!! as JWTPrincipal)
    jwt.getClaim("azp", String::class) ?: jwt.getClaim("appid", String::class) ?: "n/a (fant ikke)"
} catch (ex: Exception) {
    tjenestekallLog.error("Klarte ikke å utlede Application ID", ex)
    "n/a (feil)"
}

private fun ApplicationCall.logRequest() = tjenestekallLog.info("Mottok request mot ${request.path()} fra ${applicationId()}")
private fun String.asLocalDateOrNull() = kotlin.runCatching { LocalDate.parse(this) }.getOrNull()
