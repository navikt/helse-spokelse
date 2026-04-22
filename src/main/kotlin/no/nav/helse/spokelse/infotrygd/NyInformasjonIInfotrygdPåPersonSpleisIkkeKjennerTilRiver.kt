package no.nav.helse.spokelse.infotrygd

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spokelse.UtbetalingVarsel

internal class NyInformasjonIInfotrygdPåPersonSpleisIkkeKjennerTilRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingVarsel: UtbetalingVarsel
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "melding_om_melding_ikke_håndtert_fordi_person_ikke_funnet")
                it.requireValue("originalt_event_name", "infotrygdendring")
            }
            validate {
                it.requireKey("fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        utbetalingVarsel.nyInformasjonIInfotrygd(packet["fødselsnummer"].asText(), null)
    }
}
