package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.spokelse.tbdutbetaling.Annullering.Companion.annullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erAnnullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetalingUtenUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.melding
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.utbetaling
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

internal class TbdUtbetalingConsumer(
    env: Map<String, String>,
    private val tbdUtbetalingDao: TbdUtbetalingDao): Runnable, AutoCloseable {
    private val kafkaConsumer = KafkaConsumer<String, String>(consumerProperties(env)).apply {
        subscribe(listOf(topic))
    }
    private var konsumerer = true

    override fun run() {
        try {
            while (konsumerer) {
                val records = kafkaConsumer.poll(Duration.ofMillis(100))
                records.forEach { record ->
                    val json = jackson.readTree(record.value())
                    val meldingSendt = record.timestamp().somLocalDateTime()
                    when {
                        json.erUtbetaling -> håndterUtbetaling(json, meldingSendt)
                        json.erAnnullering -> håndteAnnulering(json, meldingSendt)
                        json.erUtbetalingUtenUtbetaling -> håndterUtbetalingUtenUtbetaling(json, meldingSendt)
                        else -> {
                            sikkerlogg.warn("Uventet event '${json.event}' på $topic. Lagres ikke i spøkelse\n\n$json‘")
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            sikkerlogg.error("Feil ved håndtering av melding på $topic", exception)
        } finally {
            close()
        }
    }

    override fun close() {
        konsumerer = false
    }

    private fun håndterUtbetaling(json: JsonNode, meldingSendt: LocalDateTime) {
        val meldingId = tbdUtbetalingDao.lagreMelding(json.melding(meldingSendt))
        json.logg(meldingId)
        tbdUtbetalingDao.lagreUtbetaling(meldingId, json.utbetaling(meldingSendt))
    }

    private fun håndteAnnulering(json: JsonNode, meldingSendt: LocalDateTime) {
        val meldingId = tbdUtbetalingDao.lagreMelding(json.melding(meldingSendt))
        json.logg(meldingId)
        tbdUtbetalingDao.annuller(meldingId, json.annullering())
    }

    private fun håndterUtbetalingUtenUtbetaling(json: JsonNode, meldingSendt: LocalDateTime) {
        val meldingId = tbdUtbetalingDao.lagreMelding(json.melding(meldingSendt))
        json.logg(meldingId)
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private fun JsonNode.logg(meldingId: Long) = sikkerlogg.info("Håndterer {}, {}, {}, {}",
            keyValue("event", event),
            keyValue("fødselsnummer", path("fødselsnummer").asText()),
            keyValue("korrelasjonsId", path("korrelasjonsId").asText()),
            keyValue("meldingId", "$meldingId")
        )

        private fun Long.somLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

        private val jackson = jacksonObjectMapper()
        private const val PREFIX = "tbd-utbetaling"
        private const val maxPollRecords = 200
        private val maxPollIntervalMs = Duration.ofSeconds(120 + maxPollRecords * 4.toLong()).toMillis()
        private const val topic = "tbd.utbetaling"

        private fun consumerProperties(env: Map<String, String>) = Properties().apply {
            // Credentials
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getValue("KAFKA_BROKERS"))
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.getValue("KAFKA_TRUSTSTORE_PATH"))
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.getValue("KAFKA_KEYSTORE_PATH"))
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
            put(ConsumerConfig.GROUP_ID_CONFIG, "$PREFIX-spokelse-v1")
            generateInstanceId(env).also { instanceId ->
                put(ConsumerConfig.CLIENT_ID_CONFIG, "$PREFIX-consumer-$instanceId")
                put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "$PREFIX-$instanceId")
            }
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "$maxPollRecords")
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "$maxPollIntervalMs")
        }

        private fun generateInstanceId(env: Map<String, String>): String {
            if (env.containsKey("NAIS_APP_NAME")) return InetAddress.getLocalHost().hostName
            return UUID.randomUUID().toString()
        }
    }
}
