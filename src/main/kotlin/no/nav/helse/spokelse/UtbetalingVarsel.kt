package no.nav.helse.spokelse

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
    private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

    override fun utbetaling(meldingId: Long, utbetaling: Utbetaling) {
        noeHarSkjedd(utbetaling.fødselsnummer)
    }

    override fun annullering(meldingId: Long, annullering: Annullering) {
        // gjør ingenting fordi Annullering-objektet inneholder ikke nok informasjon
        // TODO: hadde nok vært fint å få fødselsnummer her, ja..
    }

    private fun noeHarSkjedd(personidentifikator: String) {
        val melding = """
            {
                "personidentifikator": "$personidentifikator",
                "tidspunkt": "${OffsetDateTime.now()}"
            }
        """.trimIndent()
        producer.send(ProducerRecord(topic, melding))
        sikkerLogg.info("Pinger endring for $personidentifikator")
    }
}
