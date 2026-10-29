package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.utbetalteperioder.GroupBy.Companion.groupBy
import no.nav.sykepenger.libs.logging.loggInfo
import tools.jackson.databind.JsonNode
import java.time.LocalDate

internal class UtbetaltePerioder(
    private val infotrygd: Infotrygd,
    tbdUtbetalingApi: TbdUtbetalingApi,
    gamleUtbetalingerDao: GamleUtbetalingerDao,
) {
    private val spleis =
        Spleis(
            tbdUtbetalingApi = tbdUtbetalingApi,
            gamleUtbetalingerDao = gamleUtbetalingerDao,
        )

    internal suspend fun hent(
        request: JsonNode,
        groupBy: Set<GroupBy> = request.groupBy,
        tagsFilter: TagsFilter = AlleTags,
    ): String {
        val personidentifikatorer =
            request
                .path("personidentifikatorer")
                .values()
                .map { Personidentifikator(it.asString()) }
                .toSet()
                .takeUnless { it.isEmpty() } ?: throw IllegalArgumentException("Det må sendes med minst én personidentifikator")
        val fom = LocalDate.parse(request.path("fom").asString())
        val tom = LocalDate.parse(request.path("tom").asString())
        check(tom >= fom) { "Ugyldig periode $fom - $tom" }
        val response =
            Gruppering(
                groupBy = groupBy,
                infotrygd = infotrygd.hent(personidentifikatorer, fom, tom),
                spleis = spleis.hent(personidentifikatorer, fom, tom),
                tagsFilter = tagsFilter,
            ).gruppér()
        loggInfo("/utbetalte-perioder: request og response", "request" to request.toString(), "response" to response)
        return response
    }
}
