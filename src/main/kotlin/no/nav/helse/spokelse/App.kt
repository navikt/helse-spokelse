package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")

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
        .withKtorModule { spokelse(env.auth, dokumentDao, vedtakDao) }
        .build()
        .apply {
            NyttDokumentRiver(this, dokumentDao)
            UtbetaltRiver(this, utbetaltDao, dokumentDao)
            OldUtbetalingRiver(this, vedtakDao, dokumentDao)
            TilUtbetalingBehovRiver(this, dokumentDao)
            start()
        }
}

@KtorExperimentalAPI
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
        }
    }
}

internal fun Route.dokumenterApi(dokumentDao: DokumentDao) {
    get("/dokumenter") {
        val hendelseIder = call.request.queryParameters.getAll("hendelseId")
            ?.map { UUID.fromString(it) } ?: emptyList()
        val dokumenter = dokumentDao.finnHendelser(hendelseIder)
        call.respond(HttpStatusCode.OK, dokumenter)
    }
}

internal fun Route.grunnlagApi(vedtakDAO: VedtakDao) {
    get("/grunnlag") {
        val fødselsnummer = call.request.queryParameters["fodselsnummer"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer query param")

        tjenestekallLog.info("FP henter vedtak for $fødselsnummer")

        try {
            call.respond(HttpStatusCode.OK, vedtakDAO.hentVedtakListe(fødselsnummer))
        } catch (e: Exception) {
            tjenestekallLog.error("Feil ved henting av vedtak for $fødselsnummer", e)
            call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av vedtak")
        }
    }
}
