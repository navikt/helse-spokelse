package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerDao
import no.nav.helse.spokelse.gjenstaendesykedager.Vedtak.Companion.vedtak
import no.nav.helse.spokelse.tbdutbetaling.Annullering.Companion.annullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erAnnullering
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetalingUtenUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.melding
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.utbetaling
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.loggWarn
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

internal class TbdUtbetalingHåndterer(
    private val tbdUtbetalingDao: TbdUtbetalingDao,
    private val gjenståendeSykedagerDao: GjenståendeSykedagerDao,
    private val observers: List<TbdUtbetalingObserver>,
) {
    internal fun håndter(
        json: JsonNode,
        sendt: LocalDateTime,
    ) {
        when {
            json.erUtbetaling -> håndterUtbetaling(json, sendt)
            json.erAnnullering -> håndterAnnullering(json, sendt)
            json.erUtbetalingUtenUtbetaling -> håndterUtbetalingUtenUtbetaling(json, sendt)
            else -> loggWarn("Uventet event på ${TbdUtbetalingConsumer.TOPIC}. Lagres ikke i spøkelse", "event" to json.event, "melding" to json.toString())
        }
    }

    private fun håndterUtbetaling(
        json: JsonNode,
        sendt: LocalDateTime,
    ) {
        val meldingId = lagreMelding(json, sendt)
        lagreVedtak(meldingId, json, sendt)
        observers.forEach { it.utbetaling(meldingId, json.utbetaling(sendt)) }
    }

    private fun håndterAnnullering(
        json: JsonNode,
        sendt: LocalDateTime,
    ) {
        val meldingId = lagreMelding(json, sendt)
        gjenståendeSykedagerDao.annuller(meldingId, UUID.fromString(json.path("korrelasjonsId").asString()), sendt)
        observers.forEach { it.annullering(meldingId, json.annullering()) }
    }

    private fun håndterUtbetalingUtenUtbetaling(
        json: JsonNode,
        sendt: LocalDateTime,
    ) {
        val meldingId = lagreMelding(json, sendt)
        lagreVedtak(meldingId, json, sendt)
    }

    private fun lagreVedtak(
        meldingId: Long,
        json: JsonNode,
        sendt: LocalDateTime,
    ) {
        val vedtak = json.vedtak(sendt)
        if (!vedtak.harGyldigPeriode) {
            return loggWarn(
                "Lagrer ikke gjenstående sykedager for vedtak uten gyldig periode",
                "korrelasjonsId" to "${vedtak.korrelasjonsId}",
                "fom" to "${vedtak.fom}",
                "tom" to "${vedtak.tom}",
            )
        }
        gjenståendeSykedagerDao.lagreVedtak(meldingId, vedtak)
    }

    private fun lagreMelding(
        json: JsonNode,
        sendt: LocalDateTime,
    ): Long {
        val meldingId = tbdUtbetalingDao.lagreMelding(json.melding(sendt))
        loggInfo(
            "Håndterer melding fra ${TbdUtbetalingConsumer.TOPIC}",
            "event" to json.event,
            "fødselsnummer" to json.path("fødselsnummer").asString(),
            "korrelasjonsId" to json.path("korrelasjonsId").asString(),
            "meldingId" to "$meldingId",
        )
        return meldingId
    }
}
