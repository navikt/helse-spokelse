package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.spokelse.gamlevedtak.HentVedtakDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbtalingApi
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val objectMapper = jacksonObjectMapper()
private const val SpapiDev = "885a87c7-d4c4-4ace-8b1c-a8da50eb719c"
private const val SpapiProd = "11c3bc3d-1da7-4598-a8c4-73bead228a90"
internal fun Map<String, String>.hent(key: String) = get(key) ?: throw IllegalStateException("Mangler config for $key")
private val ApplicationCall.applicationId get() = this
    .principal<JWTPrincipal>()?.getClaim("azp", String::class)
    .takeUnless { it.isNullOrBlank() }
    ?: throw IllegalStateException("Mangler 'azp' claim i access token")
private val ApplicationCall.erSpapi get() = applicationId in setOf(SpapiDev, SpapiProd)

internal fun Route.utbetaltePerioderApi(config: Map<String, String>, httpClient: HttpClient, tbdUtbtalingApi: TbdUtbtalingApi, hentVedtakDao: HentVedtakDao) {
    val accessToken = Azure(config, httpClient)
    val infotrygd = Infotrygd(httpClient, config.hent("INFOTRYGD_SCOPE"), accessToken, config.hent("INFOTRYGD_URL"))
    val spleis = Spleis(tbdUtbtalingApi, hentVedtakDao)
    post("/utbetalte-perioder") {
        if (!call.erSpapi) {
            sikkerlogg.error("Applikasjonen ${call.applicationId} har ikke tilgang")
            return@post call.respond(Forbidden)
        }
        val request = objectMapper.readTree(call.receiveText())
        val personidentifikatorer = request.path("personidentifikatorer")
            .map { Personidentifikator(it.asText()) }
            .toSet()
            .takeUnless { it.isEmpty() } ?: throw IllegalArgumentException("Det må sendes med minst én personidentifikator")
        val fom = LocalDate.parse(request.path("fom").asText())
        val tom = LocalDate.parse(request.path("tom").asText())
        val utbetaltePerioder = Infotrygdperioder(infotrygd, personidentifikatorer, fom, tom) + SpleisPerioder(spleis, personidentifikatorer, fom, tom)
        val response = utbetaltePerioder.response
        sikkerlogg.info("/utbetalte-perioder:\nRequest:\n\t$request\nResponse:\n\t$response")
        call.respondText(response, Json)
    }
}

private val List<SpøkelsePeriode>.response get(): String {
    val utbetaltePerioder = map { objectMapper.createObjectNode().apply {
        put("personidentifikator", "${it.personidentifikator}")
        put("organisasjonsnummer", it.organisasjonsnummer)
        put("fom", "${it.fom}")
        put("tom", "${it.tom}")
        put("grad", it.grad)
        .apply {
            putArray("tags").let { tags -> it.tags.forEach(tags::add) }
        }
    }}
    return objectMapper.createObjectNode().apply {
        putArray("utbetaltePerioder").addAll(utbetaltePerioder)
    }.toString()
}
