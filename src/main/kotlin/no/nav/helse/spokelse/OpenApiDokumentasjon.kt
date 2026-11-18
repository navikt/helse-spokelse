package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.naisful.NaisEndpoints
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.ResponseConfig
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.github.smiley4.schemakenerator.core.annotations.Format
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.RequiredHandling
import io.github.smiley4.schemakenerator.swagger.data.RefType
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.Optional

internal const val AZURE_AD = "azureAd"

internal object Tags {
    const val GJENSTÅENDE_SYKEDAGER = "Gjenstående sykedager"
    const val UTBETALTE_PERIODER = "Utbetalte perioder"
}

private val valgfrieFelterSomKanVæreNull = mapOf("ApiUtbetaltPeriode" to setOf("organisasjonsnummer"))

private object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor(LocalDate::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: LocalDate,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

internal object OptionalStringSerializer : KSerializer<Optional<String>> {
    override val descriptor = PrimitiveSerialDescriptor("${Optional::class.qualifiedName}<String>", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Optional<String>,
    ) = if (value.isPresent) encoder.encodeString(value.get()) else encoder.encodeNull()

    override fun deserialize(decoder: Decoder): Optional<String> = Optional.of(decoder.decodeString())
}

private val kontekstuelleSerializers = SerializersModule { contextual(LocalDate::class, LocalDateIsoSerializer) }

private val openApiObjectMapper = jacksonObjectMapper()

private val naisEndepunkter =
    with(NaisEndpoints.Default) {
        setOf(isaliveEndpoint, isreadyEndpoint, metricsEndpoint, preStopEndpoint).map { it.trimStart('/') }.toSet()
    }

internal fun Application.openApiDokumentasjon() {
    install(OpenApi) {
        info {
            title = "Spøkelse"
            version = "1.0.0"
            description = INFO
        }
        server {
            url = "https://spokelse.intern.dev.nav.no"
            description = "dev"
        }
        server {
            url = "https://spokelse.intern.nav.no"
            description = "prod"
        }
        pathFilter = { _, url -> url.firstOrNull() !in naisEndepunkter }
        tags {
            tag(Tags.GJENSTÅENDE_SYKEDAGER) {
                description = "Antall sykedager en person har igjen, og hva tallet bygger på."
            }
            tag(Tags.UTBETALTE_PERIODER) {
                description = "Utbetalte sykepengeperioder fra Spleis og Infotrygd. Hver konsument har sin egen variant."
            }
        }
        schemas {
            generator =
                SchemaGenerator.kotlinx {
                    serializersModule = kontekstuelleSerializers
                    referencePath = RefType.OPENAPI_SIMPLE
                    title = null
                    nullables = RequiredHandling.REQUIRED
                    overwrite(SchemaGenerator.TypeOverwrites.LocalDate())
                }
        }
        examples {
            encoder { _, eksempel -> eksempel?.let { openApiObjectMapper.convertValue(it, Any::class.java) } }
        }
        security {
            securityScheme(AZURE_AD) {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
                description = "Azure AD-token med spøkelse som audience og en av rollene endepunktet krever i `roles`-claimet."
            }
        }
        postBuild = { openApi, _ ->
            openApi.externalDocs = null
            openApi.components?.schemas?.forEach { (navn, skjema) ->
                val kanVæreNull = valgfrieFelterSomKanVæreNull[navn].orEmpty()
                skjema.properties
                    ?.filterKeys { it !in skjema.required.orEmpty() }
                    ?.forEach { (feltnavn, felt) ->
                        felt.types = if (feltnavn in kanVæreNull) felt.types?.plus("null") else felt.types?.minus("null")
                    }
            }
            openApi.paths
                ?.values
                ?.flatMap { it.readOperations() }
                ?.forEach { operasjon ->
                    operasjon.responses =
                        operasjon.responses?.entries?.sortedBy { it.key }?.fold(ApiResponses()) { sortert, (kode, svar) ->
                            sortert.addApiResponse(kode, svar)
                        }
                }
        }
    }
    routing {
        route("openapi.json") {
            openApi()
        }
        route("swagger") {
            swaggerUI("/openapi.json")
        }
    }
}

internal fun ResponseConfig.problemJson() = body<ApiProblem> { mediaTypes = setOf(ContentType.Application.ProblemJson) }

// Formatet kommer fra defaultStatusPagesConfig i naisful-app.
@Serializable
@Description("Problem Details (RFC 9457). Spøkelse svarer med dette når tilgangen mangler eller noe uventet går galt.")
internal data class ApiProblem(
    @Description("Hva slags feil det er.")
    @Format("uri")
    @Example("urn:error:internal_error")
    val type: String,
    @Description("Kort beskrivelse av statuskoden.")
    @Example("Internal Server Error")
    val title: String,
    @Description("HTTP-statuskoden.")
    val status: Int,
    @Description("Mer om feilen, for mennesker. Ikke bruk den til å styre logikk.")
    val detail: String?,
    @Description("Stien i requesten som feilet.")
    @Format("uri")
    @Example("/gjenstaende-sykedager")
    val instance: String,
    @Description("Id-en til kallet. Oppgi den hvis du spør oss om feilen.")
    val callId: String?,
    @Description("Stacktracen ved uventede feil. Bare til feilsøking. Ikke bruk den til å styre logikk.")
    val stacktrace: String?,
)

private val INFO =
    """
    Spøkelse deler informasjon om utbetalte sykepengeperioder og gjenstående sykedager fra Spleis og Infotrygd.

    Alle endepunktene krever et Azure AD-token (bearer JWT) med spøkelse som audience og en av rollene
    endepunktet krever i `roles`-claimet. Hvilke roller som gir tilgang, står i beskrivelsen av hvert endepunkt.
    Uten gyldig token svarer spøkelse `401`. Med gyldig token uten noen av rollene svarer spøkelse `403`.
    """.trimIndent()
