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

    mitm(
        session = session,
        inputQueue = System.getenv("MQ_INFOTRYGD_TIL_ARENA_INPUT"),
        outputQueue = System.getenv("MQ_INFOTRYGD_TIL_ARENA_OUTPUT")
    )
    mitm(
        session = session,
        inputQueue = System.getenv("MQ_INFOTRYGD_TIL_ARENA_KVITTERING_INPUT"),
        outputQueue = System.getenv("MQ_INFOTRYGD_TIL_ARENA_KVITTERING_OUTPUT")
    )
    mitm(
        session = session,
        inputQueue = System.getenv("MQ_ARENA_TIL_INFOTRYGD_INPUT"),
        outputQueue = System.getenv("MQ_ARENA_TIL_INFOTRYGD_OUTPUT")
    )
    mitm(
        session = session,
        inputQueue = System.getenv("MQ_ARENA_TIL_INFOTRYGD_KVITTERING_INPUT"),
        outputQueue = System.getenv("MQ_ARENA_TIL_INFOTRYGD_KVITTERING_OUTPUT")
    )
}

fun mitm(session: Session, inputQueue: String, outputQueue: String) {
    val consumer = session.createConsumer(session.createQueue(inputQueue))
    val producer = session.createProducer(session.createQueue(outputQueue))
    consumer.setMessageListener {
        val body = it.getBody(String::class.java)
        log.debug("Router melding fra $inputQueue til $outputQueue")
        tjenestekallLog.debug(
            "Interceptet melding mellom $inputQueue og $outputQueue, {}",
            keyValue("innhold", body)
        )
        producer.send(it)
    }
}
