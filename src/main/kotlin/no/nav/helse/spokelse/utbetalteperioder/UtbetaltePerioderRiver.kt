package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.*

internal class UtbetaltePerioderRiver(
    rapidsConnection: RapidsConnection,
    private val utbetaltePerioder: UtbetaltePerioder
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetalte-perioder")
                it.requireKey("@id","personidentifikatorer", "fom", "tom", "oppløsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        withMDC("callId" to packet["@id"].asText()) {
            val request = objectMapper.readTree(packet.toJson())
            runBlocking { utbetaltePerioder.hent(request) }
        }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
