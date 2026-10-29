package no.nav.helse.spokelse.utbetalteperioder

import io.ktor.client.*
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.hent
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.utbetalteperioder.GroupBy.Companion.groupBy
import no.nav.sykepenger.libs.logging.loggInfo
import tools.jackson.databind.JsonNode
import java.time.LocalDate

internal class UtbetaltePerioder(
    config: Map<String, String>,
    httpClient: HttpClient,
    tbdUtbetalingApi: TbdUtbetalingApi,
    gamleUtbetalingerDao: GamleUtbetalingerDao,
) {
    private val spleis = Spleis(tbdUtbetalingApi = tbdUtbetalingApi, gamleUtbetalingerDao = gamleUtbetalingerDao)
    private val infotrygd =
        Infotrygd(
            httpClient = httpClient,
            scope = config.hent("INFOTRYGD_SCOPE"),
            accessToken = Azure(config, httpClient),
            url = config.hent("INFOTRYGD_URL"),
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
                .map { Personidentifikator(it.asText()) }
                .toSet()
                .takeUnless { it.isEmpty() } ?: throw IllegalArgumentException("Det må sendes med minst én personidentifikator")
        val fom = LocalDate.parse(request.path("fom").asText())
        val tom = LocalDate.parse(request.path("tom").asText())
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
