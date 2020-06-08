package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River


class VedtakV2River(rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey("opprettet", "aktørId", "fødselsnummer", "forbrukteSykedager")
                it.requireArray("utbetalt") {
                    requireKey("mottaker", "fagområde", "fagsystemId", "totalbeløp", "utbetalingslinjer")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {

    }
}
