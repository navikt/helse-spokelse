package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class VedtakRiver(rapidsConnection: RapidsConnection, private val vedtakDao: VedtakDao): River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("noe") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDao.save(packet.toVedtak())
    }
}

private fun JsonMessage.toVedtak() = Vedtak(this["id"].asInt())
