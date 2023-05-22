package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.WEEKS

internal class HelsesjekkRiver(
    rapidsConnection: RapidsConnection,
    private val dao: TbdUtbetalingDao
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "hel_time")
                it.requireKey("system_participating_services")
                it.rejectValues("ukedag", listOf("SATURDAY", "SUNDAY"))
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (ignorer) return

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
        private val ignorer get() = LocalDateTime.now().hour != 9

        class Helsesjekk(dao: TbdUtbetalingDao, private val systemParticipatingServices: JsonNode) {
            private val arbeidsgiverutbetalinger = dao.arbeidsgiverutbetalinger(arbeidsgiverutbetalingerTidsrom)
            private val arbeidsgiverannulleringer = dao.arbeidsgiverannulleringer(arbeidsgiverAnnulleringerTidsrom)
            private val personutbetalinger = dao.personutbetalinger(personutbetalingerTidsrom)
            private val personannulleringer = dao.personannulleringer(personannulleringerTidsrom)

            private val farePåFerde = listOf(arbeidsgiverutbetalinger, personutbetalinger, arbeidsgiverannulleringer, personannulleringer).any { it == 0 }

            private val melding get() = """
Hen husker så godt Spøkelse-gate, og ønsker nødig at jeg skal havne i samme situasjon igjen!
Så jeg tok en titt på utbetalingene jeg har registret i det siste, ser ikke dette litt lite ut? :pinching_hand:

:briefcase: Arbeidsgiver :briefcase:
Utbetalinger siste ${arbeidsgiverutbetalingerTidsrom.first} ${arbeidsgiverutbetalingerTidsrom.second.name}: $arbeidsgiverutbetalinger
Annulleringer siste ${arbeidsgiverAnnulleringerTidsrom.first} ${arbeidsgiverAnnulleringerTidsrom.second.name}: $arbeidsgiverannulleringer

:face_with_thermometer: Person :face_with_thermometer:
Utbetalinger siste ${personutbetalingerTidsrom.first} ${personutbetalingerTidsrom.second.name}: $personutbetalinger
Annulleringer siste ${personannulleringerTidsrom.first} ${personannulleringerTidsrom.second.name}: $personannulleringer

- Deres erbødig SPøkelse :ghostie:
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
