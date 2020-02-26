package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InfotrygdClient(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun hentHistorikk(
        behovId: String,
        vedtaksperiodeId: String,
        fnr: String,
        datoForYtelse: LocalDate
    ): List<Utbetalingshistorikk> {
        val historikkFom = datoForYtelse.minusYears(3)
        val url =
            "${baseUrl}/v1/hentSykepengerListe?fnr=$fnr&fraDato=${historikkFom.format(DateTimeFormatter.ISO_DATE)}&tilDato=${datoForYtelse.format(
                DateTimeFormatter.ISO_DATE
            )}"
        val (responseCode, responseBody) = with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            setRequestProperty("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
            setRequestProperty("Accept", "application/json")

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        tjenestekallLog.info(
            "svar fra Infotrygd: url=$url responseCode=$responseCode responseBody=$responseBody",
            keyValue("id", behovId),
            keyValue("vedtaksperiodeId", vedtaksperiodeId)
        )

        if (responseCode >= 300 || responseBody == null) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from Infotrygd")
        }

        val jsonNode = objectMapper.readTree(responseBody)

        try {
            MDC.put("id", behovId)
            MDC.put("vedtaksperiodeId", vedtaksperiodeId)
            return (jsonNode["sykmeldingsperioder"] as ArrayNode).map { periodeJson ->
                Utbetalingshistorikk(periodeJson)
            }
        } finally {
            MDC.remove("id")
            MDC.remove("vedtaksperiodeID")
        }
    }
}

