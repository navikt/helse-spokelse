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
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Session

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")

@KtorExperimentalAPI
fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

@KtorExperimentalAPI
fun launchApplication(env: Environment) {
    val connection = createConnection(env.mq)

    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

    val arenaOutput = session.createConsumer(session.createQueue(env.mq.arenaOutput))
    val arenaInputKvittering = session.createConsumer(session.createQueue(env.mq.arenaInputKvittering))

    arenaOutput.setMessageListener {
        tjenestekallLog.debug("Mottok melding fra arena {}", keyValue("data", it.getBody(String::class.java)))
    }

    arenaInputKvittering.setMessageListener {
        tjenestekallLog.debug("Mottok kvittering fra arena {}", keyValue("data", it.getBody(String::class.java)))
    }

    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val vedtakDao = VedtakDAO(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .withKtorModule { spokelse(env.auth) }
        .build().apply {
            VedtakRiver(this, vedtakDao)
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

    routing {
        authenticate {
            grunnlagApi()
        }
    }
}

internal fun Route.grunnlagApi() {
    get("/grunnlag") {
        requireNotNull(call.request.queryParameters["fodselsnummer"]) { "Mangler fødselsnummer query param" }
        call.respond(HttpStatusCode.OK)
    }
}
