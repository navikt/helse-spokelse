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

    val vedtakDao = VedtakDAO(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .withKtorModule { spokelse(env.auth, vedtakDao) }
        .build().apply {
            VedtakRiver(this, vedtakDao)
            start()
        }
}

@KtorExperimentalAPI
internal fun Application.spokelse(env: Environment.Auth, vedtakDAO: VedtakDAO) {
    azureAdAppAuthentication(env)
    install(ContentNegotiation) {
        jackson {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(JavaTimeModule())
        }
    }

    routing {
        authenticate {
            grunnlagApi(vedtakDAO)
        }
    }
}

internal fun Route.grunnlagApi(vedtakDAO: VedtakDAO) {
    get("/grunnlag") {
        val fnr = call.request.queryParameters["fodselsnummer"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler fodselsnummer query param")

        call.respond(HttpStatusCode.OK, vedtakDAO.hentVedtakListe(fnr).asFpVedtak())
    }
}

private fun List<Vedtak>.asFpVedtak() =
    map { vedtak ->
        FpVedtak(
            vedtaksreferanse = vedtak.førsteFraværsdag,
            utbetalinger = vedtak.utbetalinger.map { utbetaling ->
                Utbetalingsperiode(
                    fom = utbetaling.fom,
                    tom = utbetaling.tom,
                    grad = utbetaling.grad
                )
            },
            vedtattTidspunkt = vedtak.opprettet
        )
    }
