package no.nav.helse.spokelse

import java.time.OffsetDateTime
import no.nav.helse.spokelse.tbdutbetaling.Annullering
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingObserver
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

/*
    dings som legger fnr og timestamp på en helt egen topic hver gang spøkelse får vite om en utbetaling
 */
internal class UtbetalingVarsel(private val producer: KafkaProducer<String, String>, private val topic: String = "tbd.boo"): TbdUtbetalingObserver {

    override fun utbetaling(meldingId: Long, utbetaling: Utbetaling) {
        noeHarSkjedd(utbetaling.fødselsnummer, "utbetaling")
    }

    override fun annullering(meldingId: Long, annullering: Annullering) {
        noeHarSkjedd(annullering.fødselsnummer, "annullering")
    }

    private fun noeHarSkjedd(personidentifikator: String, pga: String) {
        @Language("JSON")
        val melding = """
            {
                "personidentifikator": "$personidentifikator",
                "tidspunkt": "${OffsetDateTime.now()}"
            }
        """.trimIndent()
        producer.send(ProducerRecord(topic, melding))
        sikkerLogg.info("Sier bø! som følge av en $pga for $personidentifikator:\n$melding")
    }

    private companion object {
        val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }
}
