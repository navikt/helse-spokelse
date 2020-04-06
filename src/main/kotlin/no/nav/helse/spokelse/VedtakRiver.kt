package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import java.util.*

class VedtakRiver(rapidsConnection: RapidsConnection, private val vedtakDAO: VedtakDAO) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("fnr", "vedtaksperiodeId", "fom", "tom", "grad") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDAO.save(packet.toVedtak())
    }
}

private fun JsonMessage.toVedtak() = Vedtak(
    fnr = this["fnr"].asText(),
    vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText()),
    fom = this["fom"].asLocalDate(),
    tom = this["tom"].asLocalDate(),
    grad = this["grad"].asDouble()
)
