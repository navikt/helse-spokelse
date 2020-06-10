package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("spokelse")

class OldUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakDao: VedtakDao,
    private val dokumentDao: DokumentDao
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey(
                    "vedtaksperiodeId",
                    "hendelser",
                    "fødselsnummer",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "opprettet"
                )
                it.interestedIn("utbetaling", "utbetalingslinjer", "gjenståendeSykedager")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDao.save(packet.toVedtak())
    }

    private fun JsonNode.toDokumenter() =
        dokumentDao.finn(map { UUID.fromString(it.asText()) })

    private fun JsonMessage.toVedtak(): OldVedtak {
        val utbetalingslinjer =
            this["utbetalingslinjer"].takeUnless { it.isMissingOrNull() }?.toList()
                ?: this["utbetaling"].flatMap { it["utbetalingslinjer"] }

        val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
        val dokumenter = this["hendelser"].toDokumenter()

        return OldVedtak(
            vedtaksperiodeId = vedtaksperiodeId,
            fødselsnummer = this["fødselsnummer"].asText(),
            orgnummer = this["organisasjonsnummer"].asText(),
            utbetalinger = utbetalingslinjer.map {
                OldUtbetaling(
                    fom = it["fom"].asLocalDate(),
                    tom = it["tom"].asLocalDate(),
                    grad = it["grad"].asDouble()
                )
            },
            opprettet = this["opprettet"].asLocalDateTime(),
            forbrukteSykedager = this["forbrukteSykedager"].asInt(),
            gjenståendeSykedager = this["gjenståendeSykedager"].takeUnless { it.isMissingOrNull() }?.asInt(),
            dokumenter = dokumenter
        ).also { log.info("Lagret gammelt vedtak med vedtakperiodeId $vedtaksperiodeId") }
    }
}


