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
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
private val sisteKjenning = LocalDateTime.parse("2022-03-16T21:53:18")

fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val vedtakDao = HentVedtakDao(dataSource)
    val annulleringDao = AnnulleringDao(dataSource)
    val tbdUtbetalingDao = TbdUtbetalingDao(dataSource)

    val tbdUtbetalingConsumer = TbdUtbetalingConsumer(env.raw, tbdUtbetalingDao)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .withKtorModule { spokelse(env.auth, vedtakDao, TbdUtbtalingApi(tbdUtbetalingDao)) }
        .build()
        .apply {
            registerRivers(annulleringDao)
            register(tbdUtbetalingConsumer)
        }
        .start()
}

internal fun RapidsConnection.registerRivers(
    annulleringDao: AnnulleringDao
) {
    AnnulleringRiver(this, annulleringDao)
}

internal fun Application.spokelse(env: Environment.Auth, vedtakDao: HentVedtakDao, tbdUtbtalingApi: TbdUtbtalingApi) {
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
        val time = measureTimeMillis {
            try {
                val vedtak = vedtakDAO.hentVedtakListe(fødselsnummer) + tbdUtbtalingApi.hentFpVedtak(fødselsnummer)
                vedtak.filter { it.vedtattTidspunkt > sisteKjenning }.takeUnless { it.isEmpty() }?.let {
                    tjenestekallLog.info("Fant ${it.size} vedtak for $fødselsnummer vedtatt etter $sisteKjenning")
                }
                call.respond(HttpStatusCode.OK, vedtak)
            } catch (e: Exception) {
                log.error("Feil ved henting av vedtak", e)
                tjenestekallLog.error("Feil ved henting av vedtak for $fødselsnummer", e)
                call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
            }
        }
        tjenestekallLog.info("FP hentet vedtak for $fødselsnummer ($time ms)")
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
        utbetalinger.filter { it.utbetaltTidspunkt?.isAfter(sisteKjenning) ?: false }.takeUnless { it.isEmpty() }?.let {
            tjenestekallLog.info("Fant ${it.size} utbetalinger utbetalt etter $sisteKjenning")
        }
        call.respond(utbetalinger)
    }
}

private fun ApplicationCall.applicationId() = try {
    val jwt = (authentication.principal!! as JWTPrincipal)
    jwt.getClaim("azp", String::class) ?: jwt.getClaim("appid", String::class) ?: "n/a (fant ikke)"
} catch (ex: Exception) {
    tjenestekallLog.error("Klarte ikke å utlede Application ID", ex)
    "n/a (feil)"
}

private fun ApplicationCall.logRequest() = tjenestekallLog.info("Mottok request mot ${request.path()} fra ${applicationId()}")
