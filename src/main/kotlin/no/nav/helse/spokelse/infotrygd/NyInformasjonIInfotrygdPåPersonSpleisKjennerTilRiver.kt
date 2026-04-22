package no.nav.helse.spokelse.infotrygd

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spokelse.UtbetalingVarsel

internal class NyInformasjonIInfotrygdPåPersonSpleisKjennerTilRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingVarsel: UtbetalingVarsel
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "ny_informasjon_i_infotrygd") }
            validate {
                it.requireKey("fødselsnummer")
                it.require("fom", JsonNode::asLocalDate)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        utbetalingVarsel.nyInformasjonIInfotrygd(packet["fødselsnummer"].asText(), packet["fom"].asLocalDate())
    }
}
