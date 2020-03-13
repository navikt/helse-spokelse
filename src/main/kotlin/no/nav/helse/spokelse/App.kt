package no.nav.helse.spokelse

import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.KtorBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Session

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")

@KtorExperimentalAPI
fun main() {
    KtorBuilder()
        .log(log)
        .port(System.getenv()["HTTP_PORT"]?.toInt() ?: 8080)
        .liveness { true }
        .readiness { true }
        .metrics(CollectorRegistry.defaultRegistry)
        .build()
        .start(false)

    val connection = createConnection(
        hostname = System.getenv("MQ_HOSTNAME"),
        port = System.getenv("MQ_PORT").toInt(),
        channel = System.getenv("MQ_CHANNEL"),
        queueManager = System.getenv("MQ_QUEUE_MANAGER"),
        username = System.getenv("MQ_USERNAME"),
        password = System.getenv("MQ_PASSWORD")
    )

    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

    val arenaOutput = session.createConsumer(session.createQueue(System.getenv("MQ_ARENA_OUTPUT")))
    val arenaInputKvittering = session.createConsumer(session.createQueue(System.getenv("MQ_ARENA_INPUT_KVITTERING")))

    arenaOutput.setMessageListener {
        tjenestekallLog.debug("Mottok melding fra arena {}", keyValue("data", it.getBody(String::class.java)))
    }

    arenaInputKvittering.setMessageListener {
        tjenestekallLog.debug("Mottok kvittering fra arena {}", keyValue("data", it.getBody(String::class.java)))
    }
}
