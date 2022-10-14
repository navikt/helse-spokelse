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
import no.nav.helse.spokelse.VedtakDao.UtbetalingDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.system.measureTimeMillis

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val dokumentDao = DokumentDao(dataSource)
    val utbetaltDao = UtbetaltDao(dataSource)
    val vedtakDao = VedtakDao(dataSource)
    val annulleringDao = AnnulleringDao(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .withKtorModule { spokelse(env.auth, dokumentDao, vedtakDao) }
        .build()
        .apply { registerRivers(dokumentDao, utbetaltDao, vedtakDao, annulleringDao) }
        .start()
}

internal fun RapidsConnection.registerRivers(
    dokumentDao: DokumentDao,
    utbetaltDao: UtbetaltDao,
    vedtakDao: VedtakDao,
    annulleringDao: AnnulleringDao
) {
    NyttDokumentRiver(this, dokumentDao)
    UtbetaltRiver(this, utbetaltDao, dokumentDao)
    OldUtbetalingRiver(this, vedtakDao, dokumentDao)
    TilUtbetalingBehovRiver(this, dokumentDao)
    AnnulleringRiver(this, annulleringDao)
}

internal fun Application.spokelse(env: Environment.Auth, dokumentDao: DokumentDao, vedtakDao: VedtakDao) {
    azureAdAppAuthentication(env)
    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(JavaTimeModule())
        }
    }
    routing {
        authenticate {
            dokumenterApi(dokumentDao)
            grunnlagApi(vedtakDao)
            utbetalingerApi(vedtakDao)
        }
    }
}

internal fun Route.dokumenterApi(dokumentDao: DokumentDao) {
    get("/dokumenter") {
        call.logRequest()
        val hendelseIder = call.request.queryParameters.getAll("hendelseId")
            ?.map { UUID.fromString(it) } ?: emptyList()
        val time = measureTimeMillis {
            try {
                call.respond(HttpStatusCode.OK, dokumentDao.finnHendelser(hendelseIder))
            } catch (e: Exception) {
                log.error("Feil ved oppslag av dokumenter", e)
                tjenestekallLog.error("Feil ved oppslag av dokumenter", e)
                call.respond(HttpStatusCode.InternalServerError, "Feil ved oppslag av dokumenter")
            }
        }
        tjenestekallLog.info("Hentet dokumenter for hendelser $hendelseIder ($time ms)")
    }
}

internal fun Route.grunnlagApi(vedtakDAO: VedtakDao) {
    get("/grunnlag") {
        call.logRequest()
        val fødselsnummer = call.request.queryParameters["fodselsnummer"]
            ?: run {
                log.error("/grunnlag Mangler fodselsnummer query param")
                return@get call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer query param")
            }
        val time = measureTimeMillis {
            try {
                val vedtak = vedtakDAO.hentVedtakListe(fødselsnummer)
                if (vedtak.isEmpty()) tjenestekallLog.info("Fant ingen vedtak for $fødselsnummer")
                else tjenestekallLog.info("Fant ${vedtak.size} vedtak for $fødselsnummer hvorav siste vedtatt ${vedtak.maxOf { it.vedtattTidspunkt }}")
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

internal fun Route.utbetalingerApi(vedtakDAO: VedtakDao) {
    post("/utbetalinger") {
        call.logRequest()
        val fødselsnumre = call.receive<List<String>>()

        call.respond(
            fødselsnumre.flatMap { fødselsnummer ->
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
                        listOf(UtbetalingDTO(
                            fødselsnummer = fødselsnummer,
                            fom = null,
                            tom = null,
                            grad = 0.0,
                            gjenståendeSykedager = null,
                            utbetaltTidspunkt = null,
                            refusjonstype = null
                        ))
                    }
            }
        )
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
