package no.nav.helse.spokelse

import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.helse.rapids_rivers.KtorBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("spokelse")

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
}
