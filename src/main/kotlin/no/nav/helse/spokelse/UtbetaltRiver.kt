package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("spokelse")

internal class UtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val utbetaltDao: UtbetaltDao,
    private val dokumentDao: DokumentDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "hendelser",
                    "oppdrag",
                    "forbrukteSykedager",
                    "gjenståendeSykedager"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val vedtak = Vedtak(
            fødselsnummer = packet["fødselsnummer"].asText(),
            orgnummer = packet["organisasjonsnummer"].asText(),
            dokumenter = packet["hendelser"].toDokumenter(),
            oppdrag = packet["oppdrag"].toOppdrag(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            forbrukteSykedager = packet["forbrukteSykedager"].asInt(),
            gjenståendeSykedager = packet["gjenståendeSykedager"].asInt(),
            opprettet = packet["@opprettet"].asLocalDateTime()
        )
        utbetaltDao.opprett(vedtak)
        log.info("Utbetaling på ${packet["aktørId"]} lagret")
    }

    private fun JsonNode.toDokumenter() = map { UUID.fromString(it.asText()) }
        .let { dokumentDao.finn(it) }
        .let { hendelser ->
            Dokumenter(
                sykmelding = hendelser.first { it.type == Dokument.Sykmelding },
                søknad = hendelser.first { it.type == Dokument.Søknad },
                inntektsmelding = hendelser.firstOrNull { it.type == Dokument.Inntektsmelding }
            )
        }

    private fun JsonNode.toOppdrag() = map {
        Vedtak.Oppdrag(
            mottaker = it["mottaker"].asText(),
            fagområde = it["fagområde"].asText(),
            fagsystemId = it["fagsystemId"].asText(),
            totalbeløp = it["totalbeløp"].asInt(),
            utbetalingslinjer = it["utbetalingslinjer"].toUtbetalingslinjer()
        )
    }

    private fun JsonNode.toUtbetalingslinjer() = map {
        Vedtak.Oppdrag.Utbetalingslinje(
            fom = it["fom"].asLocalDate(),
            tom = it["tom"].asLocalDate(),
            dagsats = it["dagsats"].asInt(),
            beløp = it["beløp"].asInt(),
            grad = it["grad"].asDouble(),
            sykedager = it["sykedager"].asInt()
        )
    }
}
