package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("spokelse")

internal class TilUtbetalingBehovRiver(rapidsConnection: RapidsConnection, private val dokumentDao: DokumentDao) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "behov")
                it.requireAll("@behov", listOf("Utbetaling"))
                it.requireKey("vedtaksperiodeId", "fagsystemId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        val fagsystemId = packet["fagsystemId"].asText()

        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)

        log.info("Vedtaksperiode $vedtaksperiodeId er koblet til fagsystemId $fagsystemId")
    }
}
