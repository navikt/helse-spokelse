package no.nav.helse.spokelse.utbetalinger

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.ApiTilgangsstyring
import no.nav.helse.spokelse.UtbetalingDTO
import no.nav.helse.spokelse.gamlevedtak.HentVedtakDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import org.slf4j.LoggerFactory

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private fun List<UtbetalingDTO>.unikeFnrMedEkteUtbetalinger() = this.filter {
    it != UtbetalingDTO(fødselsnummer = it.fødselsnummer, null, null, 0.0, null, null, null)
}.map { it.fødselsnummer }
    .toSet()


internal fun Route.utbetalingerApi(vedtakDAO: HentVedtakDao, tbdUtbetalingApi: TbdUtbetalingApi, tilgangsstyrings: ApiTilgangsstyring) {
    post("/utbetalinger") {
        tilgangsstyrings.utbetalinger(call)
        val fødselsnumre = call.receive<List<String>>()
        val utbetalinger = fødselsnumre.flatMap { fødselsnummer ->
            val annulerteFagsystemIder = vedtakDAO.hentAnnuleringerForFødselsnummer(fødselsnummer)

            vedtakDAO.hentSpissnokUtbetalinger(fødselsnummer)
                .filterNot { it.fagsystemId in annulerteFagsystemIder }
                .map {
                    UtbetalingDTO(
                        fødselsnummer = fødselsnummer,
                        fom = it.fom,
                        tom = it.tom,
                        grad = it.grad,
                        utbetaltTidspunkt = it.utbetaltTidspunkt,
                        gjenståendeSykedager = it.gjenståendeSykedager,
                        refusjonstype = it.refusjonstype
                    )
                }.ifEmpty {
                    listOf(
                        UtbetalingDTO(
                            fødselsnummer = fødselsnummer,
                            fom = null,
                            tom = null,
                            grad = 0.0,
                            gjenståendeSykedager = null,
                            utbetaltTidspunkt = null,
                            refusjonstype = null
                        )
                    )
                }
        } + tbdUtbetalingApi.hentSpissnokUtbetalinger(fødselsnumre)
        sikkerlogg.info("Spokelse ble bedt om informasjon om ${fødselsnumre.size} fnr, og fant informasjon ekte om ${utbetalinger.unikeFnrMedEkteUtbetalinger().size} fnr")
        call.respond(utbetalinger)
    }
}
