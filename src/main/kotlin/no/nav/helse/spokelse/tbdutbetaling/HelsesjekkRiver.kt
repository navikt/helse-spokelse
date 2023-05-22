package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.WEEKS

internal class HelsesjekkRiver(
    rapidsConnection: RapidsConnection,
    private val dao: TbdUtbetalingDao
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "halv_time")
                it.requireKey("system_participating_services")
                it.rejectValues("ukedag", listOf("SATURDAY", "SUNDAY"))
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val systemParticipatingServices = packet["system_participating_services"]
        try {
            val slackAlarm = Helsesjekk(dao, systemParticipatingServices).slackAlarm() ?: return sikkerlogg.info("Spøkelse fungerer som den skal")
            sikkerlogg.error("Kan se ut til at Spøkelse har problemer, sender alarm på slack:\n\t${slackAlarm}")
            context.publish(slackAlarm)
        } catch (throwable: Throwable) {
            sikkerlogg.error("Feil ved utføring av Spøkelse sin helsesjekk", throwable)
        }
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        class Helsesjekk(dao: TbdUtbetalingDao, private val systemParticipatingServices: JsonNode) {
            private val arbeidsgiverutbetalinger = dao.arbeidsgiverutbetalinger(arbeidsgiverutbetalingerTidsrom)
            private val arbeidsgiverannulleringer = dao.arbeidsgiverannulleringer(arbeidsgiverAnnulleringerTidsrom)
            private val personutbetalinger = dao.personutbetalinger(personutbetalingerTidsrom)
            private val personannulleringer = dao.personannulleringer(personannulleringerTidsrom)

            private val farePåFerde = listOf(arbeidsgiverutbetalinger, personutbetalinger, arbeidsgiverannulleringer, personannulleringer).any { it == 0 }

            private val melding get() = """
                \n
                :briefcase: ARBEIDSGIVER :briefcase:\n
                Utbetalinger siste ${arbeidsgiverutbetalingerTidsrom.first} ${arbeidsgiverutbetalingerTidsrom.second.name}: $arbeidsgiverutbetalinger\n
                Annulleringer siste ${arbeidsgiverAnnulleringerTidsrom.first} ${arbeidsgiverAnnulleringerTidsrom.second.name}: $arbeidsgiverannulleringer\n
                \n
                :face_with_thermometer: PERSON :face_with_thermometer:\n
                Utbetalinger siste ${personutbetalingerTidsrom.first} ${personutbetalingerTidsrom.second.name}: $personutbetalinger\n
                Annulleringer siste ${personannulleringerTidsrom.first} ${personannulleringerTidsrom.second.name}: $personutbetalinger

            """

            internal fun slackAlarm(): String? {
                //if (!farePåFerde) return null
                return JsonMessage.newMessage("slackmelding", mapOf(
                    "melding" to melding,
                    "level" to "ERROR",
                    "system_participating_services" to systemParticipatingServices
                )).toJson()
            }

            private companion object {
                private val arbeidsgiverutbetalingerTidsrom = 1 to HOURS
                private val arbeidsgiverAnnulleringerTidsrom = 1 to WEEKS
                private val personutbetalingerTidsrom = 1 to HOURS
                private val personannulleringerTidsrom = 1 to WEEKS
            }
        }
    }
}
