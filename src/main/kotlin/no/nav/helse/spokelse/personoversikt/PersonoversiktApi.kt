@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.spokelse.personoversikt

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import no.nav.helse.spokelse.Auditlogg
import no.nav.helse.spokelse.Tags
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.medKjernetilgangTil
import no.nav.helse.spokelse.postMedOboToken
import no.nav.helse.spokelse.problemJson
import no.nav.sykepenger.libs.logging.navngittLogger
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

private val logger = navngittLogger("no.nav.helse.spokelse.personoversikt.PersonoversiktApi")
private val objectMapper = jacksonObjectMapper()

internal const val PERSONOVERSIKT_SCOPE = "personoversikt-les"

internal fun Route.personoversiktApi(
    personoversikt: Personoversikt,
    tilgangskontroll: PopulasjonstilgangskontrollProvider,
    auditlogg: Auditlogg,
) {
    postMedOboToken("/v2/utbetalte-perioder-personoversikt", PERSONOVERSIKT_SCOPE, dokumentasjon = { personoversiktDokumentasjon() }) { saksbehandler ->
        val request = call.receiveText().somGyldigForespørsel() ?: throw BadRequestException(UGYLDIG_FORESPØRSEL)
        medKjernetilgangTil(request.personidentifikatorer, saksbehandler, tilgangskontroll, auditlogg) {
            val perioder = personoversikt.hent(request.personidentifikatorer, request.fom, request.tom)
            request.personidentifikatorer.forEach { auditlogg.lest(saksbehandler, it) }
            logger.info(
                "Svarer på /v2/utbetalte-perioder-personoversikt",
                "personidentifikatorer" to request.personidentifikatorer.joinToString(),
                "antallPerioder" to perioder.size.toString(),
            )
            call.respond(HttpStatusCode.OK, ApiPersonoversikt(perioder.map { it.tilApi() }))
        }
    }
}

private class Forespørsel(
    val personidentifikatorer: Set<Personidentifikator>,
    val fom: LocalDate,
    val tom: LocalDate,
)

private fun String.somGyldigForespørsel(): Forespørsel? =
    runCatching {
        val json = objectMapper.readTree(this)
        val personidentifikatorer =
            json
                .path("personidentifikatorer")
                .values()
                .map { Personidentifikator(it.stringValue()) }
                .toSet()
        val fom = json.dato("fom")
        val tom = json.dato("tom")
        Forespørsel(personidentifikatorer, fom, tom).takeIf { personidentifikatorer.isNotEmpty() && tom >= fom }
    }.getOrNull()

private fun JsonNode.dato(felt: String) = LocalDate.parse(path(felt).stringValue())

private const val UGYLDIG_FORESPØRSEL =
    "Ugyldig forespørsel. Body mangler eller er ikke JSON, `personidentifikatorer` er tom eller har en verdi som ikke er 11 siffer, " +
        "`fom` eller `tom` mangler, eller `tom` er før `fom`."

private fun Sykepengeperiode.tilApi(): ApiPeriode =
    when (this) {
        is SpeilV1Periode ->
            ApiSpeilV1Periode(
                fom = fom,
                tom = tom,
                yrkesaktiviteter =
                    yrkesaktiviteter.map { yrkesaktivitet ->
                        ApiSpeilV1Yrkesaktivitet(
                            yrkesaktivitet.organisasjonsnummer,
                            yrkesaktivitet.mottakere.map { mottaker ->
                                ApiSpeilV1Mottaker(mottaker.mottaker.tilApi(), mottaker.utbetalingsperioder.map { ApiSpeilV1Utbetalingsperiode(it.fom, it.tom, it.grad) })
                            },
                        )
                    },
            )
        is SpeilV2Periode ->
            ApiSpeilV2Periode(
                fom = fom,
                tom = tom,
                yrkesaktiviteter =
                    yrkesaktiviteter.map { yrkesaktivitet ->
                        ApiSpeilV2Yrkesaktivitet(
                            yrkesaktivitet.organisasjonsnummer,
                            yrkesaktivitet.mottakere.map { mottaker ->
                                ApiSpeilV2Mottaker(mottaker.utbetalingsperioder.map { ApiSpeilV2Utbetalingsperiode(it.fom, it.tom, it.grad) })
                            },
                        )
                    },
            )
        is SpeilV3Periode ->
            ApiSpeilV3Periode(
                fom = fom,
                tom = tom,
                yrkesaktiviteter =
                    yrkesaktiviteter.map { yrkesaktivitet ->
                        ApiSpeilV3Yrkesaktivitet(
                            yrkesaktivitet.organisasjonsnummer,
                            yrkesaktivitet.mottakere.map { mottaker ->
                                ApiSpeilV3Mottaker(mottaker.mottaker.tilApi(), mottaker.utbetalingsperioder.map { ApiSpeilV3Utbetalingsperiode(it.fom, it.tom, it.grad) })
                            },
                        )
                    },
            )
        is SpeilV4Periode ->
            ApiSpeilV4Periode(
                fom = fom,
                tom = tom,
                yrkesaktiviteter =
                    yrkesaktiviteter.map { yrkesaktivitet ->
                        ApiSpeilV4Yrkesaktivitet(
                            yrkesaktivitet.organisasjonsnummer,
                            yrkesaktivitet.mottakere.map { mottaker ->
                                ApiSpeilV4Mottaker(mottaker.mottaker.tilApi(), mottaker.utbetalingsperioder.map { ApiSpeilV4Utbetalingsperiode(it.fom, it.tom, it.grad) })
                            },
                        )
                    },
            )
        is InfotrygdV1Periode ->
            ApiInfotrygdV1Periode(
                fom = fom,
                tom = tom,
                yrkesaktiviteter =
                    yrkesaktiviteter.map { yrkesaktivitet ->
                        ApiInfotrygdV1Yrkesaktivitet(
                            yrkesaktivitet.organisasjonsnummer,
                            yrkesaktivitet.mottakere.map { mottaker ->
                                ApiInfotrygdV1Mottaker(
                                    mottaker.mottaker.tilApi(),
                                    mottaker.utbetalingsperioder.map { ApiInfotrygdV1Utbetalingsperiode(it.fom, it.tom, it.grad, it.usikkerGrad) },
                                )
                            },
                        )
                    },
            )
    }

private fun Mottaker.tilApi() =
    when (this) {
        Mottaker.ARBEIDSGIVER -> ApiMottaker.ARBEIDSGIVER
        Mottaker.PERSON -> ApiMottaker.PERSON
    }

private val skjemaPerVersjon =
    Versjon.entries.associateWith { versjon ->
        when (versjon) {
            Versjon.SPEIL_V1 -> ApiSpeilV1Periode::class
            Versjon.SPEIL_V2 -> ApiSpeilV2Periode::class
            Versjon.SPEIL_V3 -> ApiSpeilV3Periode::class
            Versjon.SPEIL_V4 -> ApiSpeilV4Periode::class
            Versjon.INFOTRYGD_V1 -> ApiInfotrygdV1Periode::class
        }.simpleName!!
    }

// Skjemageneratoren lager anyOf uten discriminator for sealed-typer, og lar versjon og kilde ha alle verdiene i hver variant.
internal fun tilpassSkjemaerForPersonoversikt(skjemaer: MutableMap<String, Schema<*>>) {
    val periode = skjemaer[ApiPeriode::class.simpleName] ?: return
    periode.oneOf = skjemaPerVersjon.values.map { Schema<Any>().`$ref`("#/components/schemas/$it") }
    periode.anyOf = null
    periode.discriminator =
        Discriminator()
            .propertyName("versjon")
            .mapping(skjemaPerVersjon.entries.associate { (versjon, skjema) -> versjon.name to "#/components/schemas/$skjema" })
    skjemaPerVersjon.forEach { (versjon, skjema) ->
        val egenskaper = skjemaer[skjema]?.properties ?: return@forEach
        egenskaper["versjon"] = enVerdi(versjon.name, egenskaper["versjon"]?.description)
        egenskaper["kilde"] = enVerdi(versjon.kilde.name, egenskaper["kilde"]?.description)
    }
    skjemaer.remove(ApiVersjon::class.simpleName)
    skjemaer.remove(ApiKilde::class.simpleName)
}

private fun enVerdi(
    verdi: String,
    beskrivelse: String?,
) = Schema<String>().apply {
    types = setOf("string")
    enum = listOf(verdi)
    description = beskrivelse
}

private const val ORGANISASJONSNUMMER =
    "Arbeidsgiveren. `null` når yrkesaktiviteten ikke er et arbeidsforhold, for eksempel for selvstendig næringsdrivende, " +
        "eller når organisasjonsnummeret mangler i Infotrygd."
private const val MOTTAKERE = "Én oppføring per mottaker. Minst én."
private const val MOTTAKER = "Hvem utbetalingen gikk til."
private const val UTBETALINGSPERIODER = "Utbetalingsperiodene til mottakeren. Minst én."
private const val YRKESAKTIVITETER = "Én oppføring per organisasjonsnummer i perioden. Minst én."
private const val PERIODE_FOM = "Første dag i utbetalingsperiodene i perioden."
private const val PERIODE_TOM = "Siste dag i utbetalingsperiodene i perioden."
private const val UTBETALINGSPERIODE_FOM = "Første dag i utbetalingsperioden."
private const val UTBETALINGSPERIODE_TOM = "Siste dag i utbetalingsperioden."
private const val GRAD = "Utbetalingsgraden i prosent."
private const val KILDE = "Hvor perioden kommer fra."
private const val VERSJON = "Hvilke felter som er garantert. Skiller skjemaene for periodene fra hverandre."

@Serializable
internal data class ApiPersonoversikt(
    @Description("Periodene, sortert etter `fom`. Tom når personen ikke har utbetalinger mellom `fom` og `tom`.")
    val perioder: List<ApiPeriode>,
)

@Serializable
internal enum class ApiKilde { SPEIL, INFOTRYGD }

@Serializable
internal enum class ApiVersjon { SPEIL_V1, SPEIL_V2, SPEIL_V3, SPEIL_V4, INFOTRYGD_V1 }

@Serializable
internal enum class ApiMottaker { ARBEIDSGIVER, PERSON }

@Serializable
internal sealed interface ApiPeriode

@Serializable
@Description("Periode fra `gamle_utbetalinger` i Speil, utbetalt før 16.03.2022.")
internal data class ApiSpeilV1Periode(
    @Description(KILDE) @Required val kilde: ApiKilde = ApiKilde.SPEIL,
    @Description(VERSJON) @Required val versjon: ApiVersjon = ApiVersjon.SPEIL_V1,
    @Description(PERIODE_FOM) val fom: LocalDate,
    @Description(PERIODE_TOM) val tom: LocalDate,
    @Description(YRKESAKTIVITETER) val yrkesaktiviteter: List<ApiSpeilV1Yrkesaktivitet>,
) : ApiPeriode

@Serializable
internal data class ApiSpeilV1Yrkesaktivitet(
    @Description(ORGANISASJONSNUMMER) @Example("999999999") val organisasjonsnummer: String?,
    @Description(MOTTAKERE) val mottakere: List<ApiSpeilV1Mottaker>,
)

@Serializable
internal data class ApiSpeilV1Mottaker(
    @Description(MOTTAKER) val mottaker: ApiMottaker,
    @Description(UTBETALINGSPERIODER) val utbetalingsperioder: List<ApiSpeilV1Utbetalingsperiode>,
)

@Serializable
internal data class ApiSpeilV1Utbetalingsperiode(
    @Description(UTBETALINGSPERIODE_FOM) val fom: LocalDate,
    @Description(UTBETALINGSPERIODE_TOM) val tom: LocalDate,
    @Description(GRAD) val grad: Int,
)

@Serializable
@Description("Periode fra `old_vedtak` og `old_utbetaling` i Speil, utbetalt før 16.03.2022. Har ikke mottaker.")
internal data class ApiSpeilV2Periode(
    @Description(KILDE) @Required val kilde: ApiKilde = ApiKilde.SPEIL,
    @Description(VERSJON) @Required val versjon: ApiVersjon = ApiVersjon.SPEIL_V2,
    @Description(PERIODE_FOM) val fom: LocalDate,
    @Description(PERIODE_TOM) val tom: LocalDate,
    @Description(YRKESAKTIVITETER) val yrkesaktiviteter: List<ApiSpeilV2Yrkesaktivitet>,
) : ApiPeriode

@Serializable
internal data class ApiSpeilV2Yrkesaktivitet(
    @Description(ORGANISASJONSNUMMER) @Example("999999999") val organisasjonsnummer: String?,
    @Description("Alltid én oppføring, fordi kilden ikke vet hvem som er mottaker.") val mottakere: List<ApiSpeilV2Mottaker>,
)

@Serializable
internal data class ApiSpeilV2Mottaker(
    @Description(UTBETALINGSPERIODER) val utbetalingsperioder: List<ApiSpeilV2Utbetalingsperiode>,
)

@Serializable
internal data class ApiSpeilV2Utbetalingsperiode(
    @Description(UTBETALINGSPERIODE_FOM) val fom: LocalDate,
    @Description(UTBETALINGSPERIODE_TOM) val tom: LocalDate,
    @Description(GRAD) val grad: Int,
)

@Serializable
@Description("Periode fra `vedtak`, `oppdrag` og `utbetaling` i Speil, utbetalt før 16.03.2022.")
internal data class ApiSpeilV3Periode(
    @Description(KILDE) @Required val kilde: ApiKilde = ApiKilde.SPEIL,
    @Description(VERSJON) @Required val versjon: ApiVersjon = ApiVersjon.SPEIL_V3,
    @Description(PERIODE_FOM) val fom: LocalDate,
    @Description(PERIODE_TOM) val tom: LocalDate,
    @Description(YRKESAKTIVITETER) val yrkesaktiviteter: List<ApiSpeilV3Yrkesaktivitet>,
) : ApiPeriode

@Serializable
internal data class ApiSpeilV3Yrkesaktivitet(
    @Description(ORGANISASJONSNUMMER) @Example("999999999") val organisasjonsnummer: String?,
    @Description(MOTTAKERE) val mottakere: List<ApiSpeilV3Mottaker>,
)

@Serializable
internal data class ApiSpeilV3Mottaker(
    @Description(MOTTAKER) val mottaker: ApiMottaker,
    @Description(UTBETALINGSPERIODER) val utbetalingsperioder: List<ApiSpeilV3Utbetalingsperiode>,
)

@Serializable
internal data class ApiSpeilV3Utbetalingsperiode(
    @Description(UTBETALINGSPERIODE_FOM) val fom: LocalDate,
    @Description(UTBETALINGSPERIODE_TOM) val tom: LocalDate,
    @Description(GRAD) val grad: Int,
)

@Serializable
@Description("Periode fra `tbd.utbetaling` i Speil, utbetalt etter 16.03.2022.")
internal data class ApiSpeilV4Periode(
    @Description(KILDE) @Required val kilde: ApiKilde = ApiKilde.SPEIL,
    @Description(VERSJON) @Required val versjon: ApiVersjon = ApiVersjon.SPEIL_V4,
    @Description(PERIODE_FOM) val fom: LocalDate,
    @Description(PERIODE_TOM) val tom: LocalDate,
    @Description(YRKESAKTIVITETER) val yrkesaktiviteter: List<ApiSpeilV4Yrkesaktivitet>,
) : ApiPeriode

@Serializable
internal data class ApiSpeilV4Yrkesaktivitet(
    @Description(ORGANISASJONSNUMMER) @Example("999999999") val organisasjonsnummer: String?,
    @Description(MOTTAKERE) val mottakere: List<ApiSpeilV4Mottaker>,
)

@Serializable
internal data class ApiSpeilV4Mottaker(
    @Description(MOTTAKER) val mottaker: ApiMottaker,
    @Description(UTBETALINGSPERIODER) val utbetalingsperioder: List<ApiSpeilV4Utbetalingsperiode>,
)

@Serializable
internal data class ApiSpeilV4Utbetalingsperiode(
    @Description(UTBETALINGSPERIODE_FOM) val fom: LocalDate,
    @Description(UTBETALINGSPERIODE_TOM) val tom: LocalDate,
    @Description(GRAD) val grad: Int,
)

@Serializable
@Description("Periode fra Infotrygd.")
internal data class ApiInfotrygdV1Periode(
    @Description(KILDE) @Required val kilde: ApiKilde = ApiKilde.INFOTRYGD,
    @Description(VERSJON) @Required val versjon: ApiVersjon = ApiVersjon.INFOTRYGD_V1,
    @Description(PERIODE_FOM) val fom: LocalDate,
    @Description(PERIODE_TOM) val tom: LocalDate,
    @Description(YRKESAKTIVITETER) val yrkesaktiviteter: List<ApiInfotrygdV1Yrkesaktivitet>,
) : ApiPeriode

@Serializable
internal data class ApiInfotrygdV1Yrkesaktivitet(
    @Description(ORGANISASJONSNUMMER) @Example("999999999") val organisasjonsnummer: String?,
    @Description(MOTTAKERE) val mottakere: List<ApiInfotrygdV1Mottaker>,
)

@Serializable
internal data class ApiInfotrygdV1Mottaker(
    @Description(MOTTAKER) val mottaker: ApiMottaker,
    @Description(UTBETALINGSPERIODER) val utbetalingsperioder: List<ApiInfotrygdV1Utbetalingsperiode>,
)

@Serializable
internal data class ApiInfotrygdV1Utbetalingsperiode(
    @Description(UTBETALINGSPERIODE_FOM) val fom: LocalDate,
    @Description(UTBETALINGSPERIODE_TOM) val tom: LocalDate,
    @Description(GRAD) val grad: Int,
    @Description(
        "`true` når graden ikke kan avgjøres sikkert, for eksempel når to perioder hos ulike arbeidsgivere overlapper og begge har grad 100.",
    )
    val usikkerGrad: Boolean,
)

@Serializable
internal data class ApiPersonoversiktRequest(
    @Description("Fødselsnummerne eller D-nummerne til personen, hvert på 11 siffer. Minst én.")
    val personidentifikatorer: List<String>,
    @Description("Første dag det skal hentes utbetalinger for.")
    val fom: LocalDate,
    @Description("Siste dag det skal hentes utbetalinger for. Kan ikke være før `fom`.")
    val tom: LocalDate,
)

private fun RouteConfig.personoversiktDokumentasjon() {
    tags(Tags.UTBETALTE_PERIODER)
    operationId = "hentUtbetaltePerioderPersonoversiktV2"
    summary = "Hent sykepengeperioder for Personoversikt"
    description = BESKRIVELSE
    request {
        body<ApiPersonoversiktRequest> {
            required = true
            example("enPerson") { value = ApiPersonoversiktRequest(listOf("12345678911"), LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31")) }
        }
    }
    response {
        code(HttpStatusCode.OK) {
            description = "Oppslaget gikk bra. Listen er tom når personen ikke har utbetalinger mellom `fom` og `tom`."
            body<ApiPersonoversikt> {
                example("speilOgInfotrygd") {
                    summary = "En eldre periode uten mottaker, delvis refusjon i Speil og usikker grad i Infotrygd"
                    value = EKSEMPEL
                }
            }
        }
        code(HttpStatusCode.BadRequest) {
            description = UGYLDIG_FORESPØRSEL
            problemJson()
        }
        code(HttpStatusCode.Forbidden) {
            description =
                "Tokenet er ikke et OBO-token for en saksbehandler eller mangler scopet `$PERSONOVERSIKT_SCOPE`, " +
                    "eller saksbehandleren mangler tilgang til en av personene etter kjerneregelsettet i Tilgangsmaskinen."
            problemJson()
        }
        code(HttpStatusCode.NotFound) {
            description = "Tilgangsmaskinen fant ikke en av personene."
            problemJson()
        }
        code(HttpStatusCode.ServiceUnavailable) {
            description = "Tilgangsmaskinen svarte ikke, eller svarte uventet. Prøv igjen senere."
            problemJson()
        }
    }
}

private fun dato(tekst: String) = LocalDate.parse(tekst)

private val EKSEMPEL =
    ApiPersonoversikt(
        listOf(
            ApiSpeilV2Periode(
                fom = dato("2021-03-01"),
                tom = dato("2021-03-31"),
                yrkesaktiviteter =
                    listOf(
                        ApiSpeilV2Yrkesaktivitet(
                            "111111111",
                            listOf(ApiSpeilV2Mottaker(listOf(ApiSpeilV2Utbetalingsperiode(dato("2021-03-01"), dato("2021-03-31"), 100)))),
                        ),
                    ),
            ),
            ApiSpeilV4Periode(
                fom = dato("2024-01-01"),
                tom = dato("2024-01-31"),
                yrkesaktiviteter =
                    listOf(
                        ApiSpeilV4Yrkesaktivitet(
                            "111111111",
                            listOf(
                                ApiSpeilV4Mottaker(ApiMottaker.ARBEIDSGIVER, listOf(ApiSpeilV4Utbetalingsperiode(dato("2024-01-01"), dato("2024-01-31"), 100))),
                                ApiSpeilV4Mottaker(ApiMottaker.PERSON, listOf(ApiSpeilV4Utbetalingsperiode(dato("2024-01-16"), dato("2024-01-31"), 100))),
                            ),
                        ),
                    ),
            ),
            ApiInfotrygdV1Periode(
                fom = dato("2024-06-03"),
                tom = dato("2024-06-28"),
                yrkesaktiviteter =
                    listOf(
                        ApiInfotrygdV1Yrkesaktivitet(
                            "111111111",
                            listOf(
                                ApiInfotrygdV1Mottaker(
                                    ApiMottaker.ARBEIDSGIVER,
                                    listOf(ApiInfotrygdV1Utbetalingsperiode(dato("2024-06-03"), dato("2024-06-28"), 100, usikkerGrad = true)),
                                ),
                            ),
                        ),
                        ApiInfotrygdV1Yrkesaktivitet(
                            "222222222",
                            listOf(
                                ApiInfotrygdV1Mottaker(
                                    ApiMottaker.PERSON,
                                    listOf(ApiInfotrygdV1Utbetalingsperiode(dato("2024-06-03"), dato("2024-06-28"), 100, usikkerGrad = true)),
                                ),
                            ),
                        ),
                    ),
            ),
        ),
    )

private val BESKRIVELSE =
    """
    Gir sykepengeperiodene til en person fra Speil og Infotrygd, for perioder som overlapper `fom`–`tom`.

    Konsument: Personoversikt (`modiapersonoversikt-api`).

    Spøkelse sjekker kjerneregelsettet i Tilgangsmaskinen for hver person, med OBO-tokenet til saksbehandleren, og audit-logger oppslaget.
    Avslag for én av personene gir 403 for hele kallet. Spøkelse gir aldri et halvt svar: feiler Tilgangsmaskinen, Infotrygd eller Speil-dataene, feiler hele kallet.

    Hver periode har yrkesaktiviteter, hver yrkesaktivitet har mottakere, og hver mottaker har utbetalingsperioder.
    En periode samler utbetalinger som henger sammen i tid, på tvers av arbeidsgivere. Helg teller ikke som opphold.
    Utbetalingsperioder hos samme mottaker som henger sammen og har samme grad, er slått sammen.

    Hver periode har en `versjon`. Den sier hvor perioden kommer fra og hvilke felter som er garantert, og hver versjon har sitt eget skjema.
    Et felt som en versjon ikke garanterer, finnes ikke i skjemaet for den versjonen. Et felt som finnes, er aldri `null`, unntatt `organisasjonsnummer`.

    | `versjon` | `kilde` | Datakilde | `mottaker` | `usikkerGrad` | Gjelder |
    |---|---|---|---|---|---|
    | `SPEIL_V1` | `SPEIL` | `gamle_utbetalinger` | ✅ | ❌ | utbetalt før 16.03.2022 |
    | `SPEIL_V2` | `SPEIL` | `old_vedtak` og `old_utbetaling` | ❌ | ❌ | utbetalt før 16.03.2022 |
    | `SPEIL_V3` | `SPEIL` | `vedtak`, `oppdrag` og `utbetaling` | ✅ | ❌ | utbetalt før 16.03.2022 |
    | `SPEIL_V4` | `SPEIL` | `tbd.utbetaling` | ✅ | ❌ | utbetalt etter 16.03.2022 |
    | `INFOTRYGD_V1` | `INFOTRYGD` | Infotrygd | ✅ | ✅ | alle perioder fra Infotrygd |

    Alle versjonene har `fom`, `tom`, `grad` og `organisasjonsnummer`. En periode har bare én versjon, så utbetalinger med ulike
    versjoner havner i ulike perioder, også når de henger sammen i tid. Når samme utbetaling finnes i flere kilder i Speil,
    er den bare med fra kilden som rangeres høyest: `SPEIL_V4` > `SPEIL_V3` > `SPEIL_V2` > `SPEIL_V1`.

    Nye felter og versjoner kommer. Konsumenten må tåle dem.
    """.trimIndent()
