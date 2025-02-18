package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.hent
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.utbetalteperioder.GroupBy.Companion.groupBy
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal class UtbetaltePerioder private constructor(private val spleis: Spleis, private val infotrygd: Infotrygd) {

    internal constructor(config: Map<String, String>, httpClient: HttpClient, tbdUtbetalingApi: TbdUtbetalingApi, gamleUtbetalingerDao: GamleUtbetalingerDao): this(
        spleis = Spleis(tbdUtbetalingApi = tbdUtbetalingApi, gamleUtbetalingerDao = gamleUtbetalingerDao),
        infotrygd = Infotrygd(
            httpClient = httpClient,
            scope = config.hent("INFOTRYGD_SCOPE"),
            accessToken = Azure(config, httpClient),
            url = config.hent("INFOTRYGD_URL")
        )
    )

    internal suspend fun hent(request: JsonNode, groupBy: Set<GroupBy> = request.groupBy, tagsFilter: TagsFilter = AlleTags): String {
        val personidentifikatorer = request.path("personidentifikatorer")
            .map { Personidentifikator(it.asText()) }
            .toSet()
            .takeUnless { it.isEmpty() } ?: throw IllegalArgumentException("Det må sendes med minst én personidentifikator")
        val fom = LocalDate.parse(request.path("fom").asText())
        val tom = LocalDate.parse(request.path("tom").asText())
        check(tom >= fom) { "Ugyldig periode $fom - $tom" }
        val response = Gruppering(
            groupBy = groupBy,
            infotrygd = infotrygd.hent(personidentifikatorer, fom, tom),
            spleis = spleis.hent(personidentifikatorer, fom, tom),
            tagsFilter = tagsFilter
        ).gruppér()
        sikkerlogg.info("/utbetalte-perioder:\nRequest:\n\t$request\nResponse:\n\t$response")
        return response
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}
