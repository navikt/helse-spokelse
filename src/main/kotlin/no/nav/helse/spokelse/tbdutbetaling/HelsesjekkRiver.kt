package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.slf4j.event.Level.ERROR
import org.slf4j.event.Level.INFO
import java.time.DayOfWeek
import java.time.DayOfWeek.THURSDAY
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
                it.requireKey("system_participating_services", "@opprettet")
                it.rejectValues("ukedag", listOf("SATURDAY", "SUNDAY"))
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (!packet.utførHelsesjekk) return

        val systemParticipatingServices = packet["system_participating_services"]
        val ukedag = DayOfWeek.valueOf(packet["ukedag"].asText())

        try {
            val slackmelding = Helsesjekk(dao, systemParticipatingServices, ukedag).slackmelding() ?: return
            context.publish(slackmelding)
        } catch (throwable: Throwable) {
            sikkerlogg.error("Feil ved utføring av Spøkelse sin helsesjekk", throwable)
        }
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val JsonMessage.utførHelsesjekk get() = get("@opprettet").asLocalDateTime().let { it.hour == 8 && it.minute == 30 }

        class Helsesjekk(dao: TbdUtbetalingDao, private val systemParticipatingServices: JsonNode, ukedag: DayOfWeek) {
            private val arbeidsgiverutbetalinger = dao.arbeidsgiverutbetalinger(arbeidsgiverutbetalingerTidsrom)
            private val arbeidsgiverannulleringer = dao.arbeidsgiverannulleringer(arbeidsgiverAnnulleringerTidsrom)
            private val personutbetalinger = dao.personutbetalinger(personutbetalingerTidsrom)
            private val personannulleringer = dao.personannulleringer(personannulleringerTidsrom)

            private val farePåFerde = listOf(arbeidsgiverutbetalinger, personutbetalinger, arbeidsgiverannulleringer, personannulleringer).any { it == 0 }
            private val gladmelding = ukedag == THURSDAY

            private val oppsummering get() = """
:briefcase: Arbeidsgiver :briefcase:
Utbetalinger siste ${arbeidsgiverutbetalingerTidsrom.first} ${arbeidsgiverutbetalingerTidsrom.second.name}: $arbeidsgiverutbetalinger
Annulleringer siste ${arbeidsgiverAnnulleringerTidsrom.first} ${arbeidsgiverAnnulleringerTidsrom.second.name}: $arbeidsgiverannulleringer

:face_with_thermometer: Person :face_with_thermometer:
Utbetalinger siste ${personutbetalingerTidsrom.first} ${personutbetalingerTidsrom.second.name}: $personutbetalinger
Annulleringer siste ${personannulleringerTidsrom.first} ${personannulleringerTidsrom.second.name}: $personannulleringer

- Deres erbødig SPøkelse :ghostie:
"""

            private val varsel get() = """

Hen husker så godt Spøkelse-gate, og ønsker nødig at jeg skal havne i samme situasjon igjen!
Så jeg tok en titt på utbetalingene jeg har registret i det siste, ser ikke dette litt lite ut? :pinching_hand:

$oppsummering
"""

            private val melding get() = """

Bare en oppsummering fra meg denne gangen, jeg ser ikke noe bekymringsverdig med disse tallen. Gjør du? :index_pointing_at_the_viewer:

$oppsummering
"""

            internal fun slackmelding(): String? {
                if (farePåFerde) return lagSlackmelding(ERROR, varsel)
                if (gladmelding) return lagSlackmelding(INFO, melding)
                return null
            }

            private fun lagSlackmelding(level: Level, melding: String) = JsonMessage.newMessage("slackmelding", mapOf(
                "melding" to melding,
                "level" to level.name,
                "system_participating_services" to systemParticipatingServices
            )).toJson().also {
                if (level == ERROR) sikkerlogg.error("Kan se ut til at Spøkelse har problemer, sender alarm på slack:\n\t${it}")
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
