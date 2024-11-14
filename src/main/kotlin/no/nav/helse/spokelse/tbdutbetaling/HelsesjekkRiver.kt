package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
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
            precondition { it.requireValue("@event_name", "spokelse_helsesjekk") }
            validate {
                it.requireKey("system_participating_services", "@opprettet")
                it.interestedIn("ukedag")
            }
        }.register(this)
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "halv_time")
                it.forbidValues("ukedag", listOf("SATURDAY", "SUNDAY"))
            }
            validate {
                it.requireKey("system_participating_services", "@opprettet")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        if (!packet.utførHelsesjekk) return

        val systemParticipatingServices = packet["system_participating_services"]
        val ukedag = packet["ukedag"].takeUnless { it.isMissingOrNull() }?.let { DayOfWeek.valueOf(it.asText()) } ?: THURSDAY

        try {
            val slackmelding = Helsesjekk(dao, systemParticipatingServices, ukedag).slackmelding() ?: return
            context.publish(slackmelding)
        } catch (throwable: Throwable) {
            sikkerlogg.error("Feil ved utføring av Spøkelse sin helsesjekk", throwable)
        }
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val JsonMessage.utførHelsesjekk get() = get("@opprettet").asLocalDateTime().let { it.hour == 8 && it.minute == 30 } || get("@event_name").asText() == "spokelse_helsesjekk"

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
Utbetalt & annullert innenfor siste ${arbeidsgiverAnnulleringerTidsrom.first} ${arbeidsgiverAnnulleringerTidsrom.second.name}: $arbeidsgiverannulleringer

:face_with_thermometer: Person :face_with_thermometer:
Utbetalinger siste ${personutbetalingerTidsrom.first} ${personutbetalingerTidsrom.second.name}: $personutbetalinger
Utbetalt & annullert innenfor siste ${personannulleringerTidsrom.first} ${personannulleringerTidsrom.second.name}: $personannulleringer

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
                else sikkerlogg.info("Sender gladmelding på slack:\n\t${it}")
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
