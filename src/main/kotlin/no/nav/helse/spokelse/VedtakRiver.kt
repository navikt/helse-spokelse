package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class VedtakRiver(rapidsConnection: RapidsConnection, private val vedtakDAO: VedtakDAO): River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("id") }
            validate { it.requireKey("fnr") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDAO.save(packet.toVedtak())
    }
}

private fun JsonMessage.toVedtak() = Vedtak(this["id"].asInt(), this["fnr"].asText())
