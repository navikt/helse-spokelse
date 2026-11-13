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
import io.github.smiley4.schemakenerator.core.annotations.Name
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.RequiredHandling
import io.github.smiley4.schemakenerator.swagger.data.RefType
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.swagger.v3.oas.models.responses.ApiResponses
import tools.jackson.module.kotlin.jacksonObjectMapper

internal const val AZURE_AD = "azureAd"

internal object Tags {
    const val GJENSTÅENDE_SYKEDAGER = "Gjenstående sykedager"
}

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
        }
        schemas {
            generator =
                SchemaGenerator.reflection {
                    referencePath = RefType.OPENAPI_SIMPLE
                    title = null
                    nullables = RequiredHandling.REQUIRED
                    overwrite(SchemaGenerator.TypeOverwrites.LocalDate())
                    overwrite(SchemaGenerator.TypeOverwrites.LocalDateTime())
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
            openApi.components?.schemas?.values?.forEach { skjema ->
                skjema.properties
                    ?.filterKeys { it !in skjema.required.orEmpty() }
                    ?.values
                    ?.forEach { felt -> felt.types = felt.types?.minus("null") }
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

internal fun ResponseConfig.problemJson() = body<ProblemDto> { mediaTypes = setOf(ContentType.Application.ProblemJson) }

// Formatet kommer fra defaultStatusPagesConfig i naisful-app.
@Name("Problem")
@Description("Problem Details (RFC 9457). Spøkelse svarer med dette når tilgangen mangler eller noe uventet går galt.")
internal data class ProblemDto(
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
