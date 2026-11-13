package no.nav.helse.spokelse.tbdutbetaling

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import no.nav.helse.spokelse.UtbetalingVarsel
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerDao
import no.nav.helse.spokelse.gjenstaendesykedager.Vedtak
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingMelding.Companion.tbdUtbetalingMelding
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.loggWarn
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import javax.sql.DataSource

internal class TbdUtbetalingHåndterer(
    private val dataSource: () -> DataSource,
    private val tbdUtbetalingDao: TbdUtbetalingDao,
    private val gjenståendeSykedagerDao: GjenståendeSykedagerDao,
    private val gamleUtbetalingerDao: GamleUtbetalingerDao,
    private val utbetalingVarsel: UtbetalingVarsel,
) {
    internal fun håndter(
        json: JsonNode,
        sendt: LocalDateTime,
    ) {
        val melding =
            json.tbdUtbetalingMelding(sendt)
                ?: return loggWarn("Uventet event på ${TbdUtbetalingConsumer.TOPIC}. Lagres ikke i spøkelse", "event" to json.event, "melding" to json.toString())

        sessionOf(dataSource()).use { session ->
            session.transaction { transaksjon -> transaksjon.lagre(melding) }
        }

        when (melding) {
            is TbdUtbetalingMelding.Utbetaling -> utbetalingVarsel.utbetaling(melding.utbetaling)
            is TbdUtbetalingMelding.Annullering -> utbetalingVarsel.annullering(melding.annullering)
            is TbdUtbetalingMelding.VedtakUtenUtbetaling -> {}
        }
    }

    private fun TransactionalSession.lagre(melding: TbdUtbetalingMelding) {
        val meldingId = tbdUtbetalingDao.lagreMelding(this, melding.råmelding)
        loggInfo(
            "Håndterer melding fra ${TbdUtbetalingConsumer.TOPIC}",
            "event" to melding.råmelding.type,
            "fødselsnummer" to melding.råmelding.fødselsnummer,
            "korrelasjonsId" to "${melding.korrelasjonsId}",
            "meldingId" to "$meldingId",
        )
        when (melding) {
            is TbdUtbetalingMelding.Utbetaling -> {
                lagreVedtak(meldingId, melding.vedtak)
                tbdUtbetalingDao.lagreUtbetaling(this, meldingId, melding.utbetaling)
            }

            is TbdUtbetalingMelding.VedtakUtenUtbetaling -> {
                lagreVedtak(meldingId, melding.vedtak)
            }

            is TbdUtbetalingMelding.Annullering -> {
                gjenståendeSykedagerDao.annuller(this, meldingId, melding.korrelasjonsId, melding.sendt)
                tbdUtbetalingDao.annuller(this, meldingId, melding.annullering)
                gamleUtbetalingerDao.lagreAnnullering(this, melding.annullering)
            }
        }
    }

    private fun TransactionalSession.lagreVedtak(
        meldingId: Long,
        vedtak: Vedtak,
    ) {
        if (!vedtak.harGyldigPeriode) {
            return loggWarn(
                "Lagrer ikke gjenstående sykedager for vedtak uten gyldig periode",
                "korrelasjonsId" to "${vedtak.korrelasjonsId}",
                "fom" to "${vedtak.fom}",
                "tom" to "${vedtak.tom}",
            )
        }
        gjenståendeSykedagerDao.lagreVedtak(this, meldingId, vedtak)
    }
}
