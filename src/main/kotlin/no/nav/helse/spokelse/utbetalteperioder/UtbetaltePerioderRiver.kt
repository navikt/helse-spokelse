package no.nav.helse.spokelse.utbetalteperioder

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.sykepenger.libs.logging.loggInfo
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

internal class UtbetaltePerioderRiver(
    rapidsConnection: RapidsConnection,
    private val utbetaltePerioder: UtbetaltePerioder,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "utbetalte-perioder") }
                validate {
                    it.requireKey("@id", "personidentifikatorer", "fom", "tom", "oppløsning")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        withMDC("callId" to packet["@id"].asString()) {
            val request = objectMapper.readValue<ApiUtbetaltePerioderMedOppløsningRequest>(packet.toJson())
            val response =
                runBlocking {
                    utbetaltePerioder.hent(request.tilPersonidentifikatorer(), request.fom, request.tom, request.tilGroupBy()).tilApiUtbetaltePerioder(request.tilGroupBy())
                }
            loggInfo("/utbetalte-perioder: request og response", "request" to packet.toJson(), "response" to objectMapper.writeValueAsString(response))
        }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
