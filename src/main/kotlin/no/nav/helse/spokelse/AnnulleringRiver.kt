package no.nav.helse.spokelse

import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val annulleringDao: AnnulleringDao
) : River.PacketListener {
    val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    init {
        River(rapidsConnection).apply {
            setupValidation("utbetaling_annullert")
        }.register(this)
        River(rapidsConnection).apply {
            setupValidation("utbetaling_annullert_replay_for_pensjon")
        }.register(this)
    }

    fun River.setupValidation(eventName: String) = validate {
        it.demandValue("@event_name", eventName)
        it.requireArray("utbetalingslinjer") {
            requireKey("fom", "tom")
        }
        it.requireKey("fødselsnummer", "organisasjonsnummer", "fagsystemId")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val linjer = packet["utbetalingslinjer"]
        val fom = linjer.minOf { it["fom"].asLocalDate() }
        val tom = linjer.maxOf { it["tom"].asLocalDate() }
        annulleringDao.insertAnnullering(packet["fødselsnummer"].asText(), packet["organisasjonsnummer"].asText(), packet["fagsystemId"].asText(), fom, tom, "SPREF")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.warn(problems.toExtendedReport())
    }
}
