package no.nav.helse.spokelse.felles

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.spokelse.gjenstaendesykedager.Infotrygdperioder
import no.nav.helse.spokelse.personoversikt.InfotrygdV1Linje
import no.nav.helse.spokelse.personoversikt.Infotrygdferie
import no.nav.helse.spokelse.personoversikt.Mottaker
import no.nav.helse.spokelse.personoversikt.utbetalingsdager
import org.intellij.lang.annotations.Language
import org.slf4j.MDC
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

internal class Infotrygd(
    private val httpClient: HttpClient,
    private val scope: String,
    private val accessTokenProvider: AccessTokenProvider,
    private val url: String,
) : Infotrygdperioder {
    suspend fun hent(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate,
    ): List<SpøkelsePeriode> = hentPerioder(personidentifikatorer, fom, tom, inkluderAllePeriodetyper = false).map { it.somPeriode() }

    // Feriedager inni en utbetalingsrad er ikke utbetalt, så ferieradene hentes i et eget kall over hele spennet til utbetalingene.
    // Sparkel gir bare rader som overlapper fom–tom, og en utbetaling kan ha ferie utenfor fom–tom.
    // Utbetalingene hentes uten de andre periodetypene, fordi Sparkel setter UsikkerGrad ut fra alle radene den gir.
    suspend fun hentUtbetalteLinjer(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate,
    ): List<InfotrygdV1Linje> {
        val utbetalinger = hentPerioder(personidentifikatorer, fom, tom, inkluderAllePeriodetyper = false)
        if (utbetalinger.isEmpty()) return emptyList()
        val ferie =
            hentPerioder(personidentifikatorer, utbetalinger.minOf { it.dato("fom") }, utbetalinger.maxOf { it.dato("tom") }, inkluderAllePeriodetyper = true)
                .filter { it.path("type").asString() == FERIE }
                .map { it.somFerie() }
        return utbetalinger.map { it.somUtbetaltLinje(ferie) }
    }

    override suspend fun hentAllePeriodetyper(
        personidentifikator: Personidentifikator,
        fom: LocalDate,
        tom: LocalDate,
    ): List<Infotrygdperiode> = hentPerioder(setOf(personidentifikator), fom, tom, inkluderAllePeriodetyper = true).map { it.somInfotrygdperiode() }

    private suspend fun hentPerioder(
        personidentifikatorer: Set<Personidentifikator>,
        fom: LocalDate,
        tom: LocalDate,
        inkluderAllePeriodetyper: Boolean,
    ): List<JsonNode> {
        val token = withContext(Dispatchers.IO) { accessTokenProvider.machineToken(scope) }
        val response =
            httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header("x-callId", MDC.get("callId"))
                @Language("JSON")
                val request = """
                {
                    "personidentifikatorer": ${personidentifikatorer.map { "\"$it\"" }},
                    "fom": "$fom",
                    "tom": "$tom",
                    "inkluderAllePeriodetyper": $inkluderAllePeriodetyper
                }
            """
                setBody(request)
            }

        check(response.status == HttpStatusCode.OK) {
            "Mottok HTTP ${response.status} ved oppslag på Infotrygddata"
        }

        return objectMapper
            .readTree(response.bodyAsText())
            .path("utbetaltePerioder")
            .values()
            .toList()
    }

    private fun JsonNode.somInfotrygdperiode(): Infotrygdperiode =
        Infotrygdperiode(
            fom = LocalDate.parse(path("fom").asString()),
            tom = LocalDate.parse(path("tom").asString()),
            type = path("type").asString(),
            maksdato = path("maksdato").takeUnless { it.isMissingOrNull() }?.let { LocalDate.parse(it.asString()) },
            organisasjonsnummer = path("organisasjonsnummer").takeUnless { it.isMissingOrNull() }?.asString(),
        )

    private fun JsonNode.dato(felt: String) = LocalDate.parse(path(felt).asString())

    private fun JsonNode.somFerie() = Infotrygdferie(Personidentifikator(path("personidentifikator").asString()), dato("skjæringstidspunkt"), dato("fom"), dato("tom"))

    private fun JsonNode.somUtbetaltLinje(ferie: List<Infotrygdferie>): InfotrygdV1Linje {
        val personidentifikator = Personidentifikator(path("personidentifikator").asString())
        val skjæringstidspunkt = dato("skjæringstidspunkt")
        val fom = dato("fom")
        val tom = dato("tom")
        val dagsats = path("dagsats").asInt()
        return InfotrygdV1Linje(
            personidentifikator = personidentifikator,
            organisasjonsnummer = path("organisasjonsnummer").takeUnless { it.isMissingOrNull() }?.asString(),
            mottaker = path("type").asString().let { type -> checkNotNull(MOTTAKER_PER_UTBETALINGSTYPE[type]) { "Uventet periodetype fra Infotrygd: $type" } },
            fom = fom,
            tom = tom,
            grad = path("grad").asInt(),
            dagsats = dagsats,
            beløp = dagsats * utbetalingsdager(personidentifikator, skjæringstidspunkt, fom, tom, ferie),
            usikkerGrad = path("tags").values().any { it.asString() == "UsikkerGrad" },
            skjæringstidspunkt = skjæringstidspunkt,
        )
    }

    private fun JsonNode.somPeriode(): SpøkelsePeriode =
        SpøkelsePeriode(
            personidentifikator = Personidentifikator(path("personidentifikator").asString()),
            fom = LocalDate.parse(path("fom").asString()),
            tom = LocalDate.parse(path("tom").asString()),
            grad = path("grad").asInt(),
            organisasjonsnummer = path("organisasjonsnummer").takeUnless { it.isMissingOrNull() }?.asString(),
            tags = (path("tags").values().map { it.asString() } + "Infotrygd").toSet(),
        )

    private companion object {
        val objectMapper = jacksonObjectMapper()

        // De samme typene som sykepengeperioder-api gir når inkluderAllePeriodetyper er false.
        val MOTTAKER_PER_UTBETALINGSTYPE =
            mapOf(
                "UTBETALING" to Mottaker.PERSON,
                "REDUKSJON_MEDLEM" to Mottaker.PERSON,
                "ARB_REF" to Mottaker.ARBEIDSGIVER,
                "REDUKSJON_ARB_REF" to Mottaker.ARBEIDSGIVER,
            )
        const val FERIE = "FERIE"
    }
}
