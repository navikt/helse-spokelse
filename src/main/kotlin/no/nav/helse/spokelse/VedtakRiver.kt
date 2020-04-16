package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.*

class VedtakRiver(rapidsConnection: RapidsConnection, private val vedtakDAO: VedtakDAO) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey(
                    "fødselsnummer",
                    "førsteFraværsdag",
                    "forbrukteSykedager",
                    "opprettet"
                )
                it.interestedIn("utbetaling")
                it.interestedIn("utbetalingslinjer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDAO.save(packet.toVedtak())
    }
}

private fun JsonMessage.toVedtak(): Vedtak {
    val utbetalingslinjer =
        this["utbetalingslinjer"].takeUnless { it.isMissingOrNull() }?.toList()
            ?: this["utbetaling"].flatMap { it["utbetalingslinjer"] }

    return Vedtak(
        fødselsnummer = this["fødselsnummer"].asText(),
        førsteFraværsdag = this["førsteFraværsdag"].asLocalDate(),
        utbetalinger = utbetalingslinjer.map {
            Utbetaling(
                fom = it["fom"].asLocalDate(),
                tom = it["tom"].asLocalDate(),
                grad = it["grad"].asDouble()
            )
        },
        opprettet = this["opprettet"].asLocalDateTime()
    )
}
