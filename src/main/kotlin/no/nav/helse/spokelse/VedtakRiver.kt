package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.*
import java.util.*

class VedtakRiver(rapidsConnection: RapidsConnection, private val vedtakDAO: VedtakDAO) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey(
                    "fødselsnummer",
                    "utbetaling",
                    "vedtaksperiodeId",
                    "forbrukteSykedager",
                    "opprettet"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDAO.save(packet.toVedtak())
    }
}

private fun JsonMessage.toVedtak() = Vedtak(
    fødselsnummer = this["fødselsnummer"].asText(),
    vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText()),
    utbetalinger = this["utbetaling"].flatMap { it["utbetalingslinjer"] }.map {
        Utbetaling(
            fom = it["fom"].asLocalDate(),
            tom = it["tom"].asLocalDate(),
            grad = it["grad"].asDouble()
        )
    },
    opprettet = this["opprettet"].asLocalDateTime()
)
