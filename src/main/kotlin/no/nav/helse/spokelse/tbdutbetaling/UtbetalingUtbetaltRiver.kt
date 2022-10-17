package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.rapids_rivers.*
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.utbetaling

internal class UtbetalingUtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val tbdUtbetalingDao: TbdUtbetalingDao) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("event", "utbetaling_utbetalt") }
            validate { it.requireKey(
                "fødselsnummer",
                "forbrukteSykedager",
                "gjenståendeSykedager"
            )}
            validate { it.interestedIn(
                "arbeidsgiverOppdrag.fagsystemId",
                "arbeidsgiverOppdrag.mottaker",
                "arbeidsgiverOppdrag.utbetalingslinjer",
                "personOppdrag.fagsystemId",
                "personOppdrag.mottaker",
                "personOppdrag.utbetalingslinjer"
            )}
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val meldingId = tbdUtbetalingDao.lagreMelding(Melding(packet))
        tbdUtbetalingDao.lagreUtbetaling(meldingId, packet.utbetaling())
    }
}
