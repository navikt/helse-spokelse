package no.nav.helse.sparkel.sykepengeperioder

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.sparkel.sykepengeperioder.infotrygd.InfotrygdClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class Sykepengehistorikkløser(
    rapidsConnection: RapidsConnection,
    private val infotrygdClient: InfotrygdClient
) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        internal val behov = "Sykepengehistorikk"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.requireAll("@behov", listOf(behov)) }
            validate { it.forbid("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.requireKey("utgangspunktForBeregningAvYtelse") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        try {
            infotrygdClient.hentHistorikk(
                behovId = packet["@id"].asText(),
                vedtaksperiodeId = packet["vedtaksperiodeId"].asText(),
                fnr = packet["fødselsnummer"].asText(),
                datoForYtelse = LocalDate.parse(packet["utgangspunktForBeregningAvYtelse"].asText())
            ).also {
                packet.setLøsning(behov, it)
            }
            log.info(
                "løser behov: {} for {}",
                keyValue("id", packet["@id"].asText()),
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
            )
            sikkerlogg.info(
                "løser behov: {} for {}",
                keyValue("id", packet["@id"].asText()),
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
            )

            context.send(packet.toJson())
        } catch (err: Exception) {
            log.error(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
            sikkerlogg.error(
                "feil ved henting av infotrygd-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
        }
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {}

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

}
