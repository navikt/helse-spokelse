package no.nav.helse.spokelse

import net.logstash.logback.argument.StructuredArguments.keyValue
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
        val fagsystemId = packet["fagsystemId"].asText()
        val linjer = packet["utbetalingslinjer"]
        val fom = linjer.minOf { it["fom"].asLocalDate() }
        val tom = linjer.maxOf { it["tom"].asLocalDate() }
        sikkerLogg.info("Inserter annullering for {} via {}",
            keyValue("fagsystemId", fagsystemId),
            keyValue("event_name", packet["@event_name"].asText()))
        annulleringDao.insertAnnullering(packet["fødselsnummer"].asText(), packet["organisasjonsnummer"].asText(), fagsystemId, fom, tom, "SPREF")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.warn(problems.toExtendedReport())
    }
}
