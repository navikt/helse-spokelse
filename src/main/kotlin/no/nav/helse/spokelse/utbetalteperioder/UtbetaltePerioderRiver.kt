package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking

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

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        withMDC("callId" to packet["@id"].asText()) {
            val request = objectMapper.readTree(packet.toJson())
            runBlocking { utbetaltePerioder.hent(request) }
        }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
