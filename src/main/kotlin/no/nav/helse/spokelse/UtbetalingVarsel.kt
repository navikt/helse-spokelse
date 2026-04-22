package no.nav.helse.spokelse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingObserver
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

/*
    dings som legger fnr og timestamp på en helt egen topic hver gang spøkelse får vite om en utbetaling
 */
internal class UtbetalingVarsel(private val producer: KafkaProducer<String, String>, private val topic: String = "tbd.boo"): TbdUtbetalingObserver {

    override fun utbetaling(meldingId: Long, utbetaling: Utbetaling) {
        noeHarSkjedd(utbetaling.fødselsnummer, utbetaling.fom, "utbetaling")
    }

    override fun annullering(meldingId: Long, annullering: Annullering) {
        noeHarSkjedd(annullering.fødselsnummer, annullering.fom, "annullering")
    }

    private fun noeHarSkjedd(personidentifikator: String, fraOgMed: LocalDate?, pga: String) {
        val melding = lagMelding(personidentifikator, fraOgMed)
        producer.send(ProducerRecord(topic, melding))
        when (val fom = fraOgMed) {
            null -> sikkerLogg.info("Sier bø! som følge av en $pga for $personidentifikator:\n$melding")
            else -> sikkerLogg.info("Sier bø! som følge av en $pga fra og med $fom for $personidentifikator:\n$melding")
        }
    }

    private companion object {
        val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        val objectmapper = jacksonObjectMapper()
        fun lagMelding(personidentifikator: String, fraOgMed: LocalDate?) = objectmapper.createObjectNode().apply {
            put("personidentifikator", personidentifikator)
            put("tidspunkt", OffsetDateTime.now().toString())
            fraOgMed?.let { put("fraOgMed", it.toString()) }
        }.toString()
    }
}
