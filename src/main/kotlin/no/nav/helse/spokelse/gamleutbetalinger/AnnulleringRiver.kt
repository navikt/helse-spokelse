package no.nav.helse.spokelse.gamleutbetalinger

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class AnnulleringRiver(
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
        it.requireKey("fødselsnummer", "organisasjonsnummer", "fom", "tom")
        it.interestedIn("arbeidsgiverFagsystemId", "personFagsystemId")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val arbeidsgiverFagsystemId = packet["arbeidsgiverFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()
        val personFagsystemId = packet["personFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()
        val fødselsnummer = packet["fødselsnummer"].asText()
        val orgnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val eventName = packet["@event_name"].asText()

        if (arbeidsgiverFagsystemId != null) {
            insertAnnullering(
                fagsystemId = arbeidsgiverFagsystemId,
                fagområde = "SPREF",
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                fom = fom,
                tom = tom,
                eventName = eventName
            )
        }
        if (personFagsystemId != null) {
            insertAnnullering(
                fagsystemId = personFagsystemId,
                fagområde = "SP",
                fødselsnummer = fødselsnummer,
                orgnummer = orgnummer,
                fom = fom,
                tom = tom,
                eventName = eventName
            )
        }
    }


    private fun insertAnnullering(fagsystemId: String, fagområde: String, fødselsnummer: String, orgnummer: String, fom: LocalDate, tom:LocalDate, eventName: String) {
        sikkerLogg.info("Inserter annullering for {} {} via {}",
            keyValue("fagsystemId", fagsystemId),
            keyValue("fagområde", fagområde),
            keyValue("event_name", eventName))
        if (annulleringDao.insertAnnullering(fødselsnummer, orgnummer, fagsystemId, fom, tom, fagområde) < 1) {
            sikkerLogg.info("Annulering for {} {} ble ikke insertet siden den ble sett som et duplikat",
                keyValue("fagsystemId", fagsystemId),
                keyValue("fagområde", fagområde))
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.warn(problems.toExtendedReport())
    }
}
