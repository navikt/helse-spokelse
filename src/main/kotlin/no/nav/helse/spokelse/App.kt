package no.nav.helse.spokelse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.security.token.support.ktor.IssuerConfig
import no.nav.security.token.support.ktor.TokenSupportConfig
import no.nav.security.token.support.ktor.tokenValidationSupport
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Session

private val log: Logger = LoggerFactory.getLogger("spokelse")
private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")

@KtorExperimentalAPI
fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

@KtorExperimentalAPI
fun launchApplication(env: Environment) {
    val connection = createConnection(env.mq)

    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

    val arenaOutput = session.createConsumer(session.createQueue(env.mq.arenaOutput))
    val arenaInputKvittering = session.createConsumer(session.createQueue(env.mq.arenaInputKvittering))

    arenaOutput.setMessageListener {
        tjenestekallLog.debug("Mottok melding fra arena {}", keyValue("data", it.getBody(String::class.java)))
    }

    arenaInputKvittering.setMessageListener {
        tjenestekallLog.debug("Mottok kvittering fra arena {}", keyValue("data", it.getBody(String::class.java)))
    }

    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val vedtakDao = VedtakDAO(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw)).withKtorModule {
        install(Authentication) {
            tokenValidationSupport(config = TokenSupportConfig(
                IssuerConfig(
                    name = env.auth.name,
                    acceptedAudience = listOf(env.auth.acceptedAudience),
                    discoveryUrl = env.auth.discoveryUrl
                )
            ),
                additionalValidation = {
                    val claims = it.getClaims(env.auth.name)
                    val groups = claims?.getAsList("groups")
                    val hasGroup = groups != null && groups.contains(env.auth.requiredGroup)
                    if (!hasGroup) log.info("missing required group ${env.auth.requiredGroup}")
                    val hasIdentRequiredForAuditLog = claims?.getStringClaim("prefered_username") != null
                    if (!hasIdentRequiredForAuditLog) log.info("missing claim prefered_username required for auditlog")
                    hasGroup && hasIdentRequiredForAuditLog
                })
        }

        install(ContentNegotiation) {
            jackson {
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }

        routing {
            authenticate {
                get("/grunnlag") {
                    requireNotNull(call.request.queryParameters["fødselsnummer"]) { "Mangler fødselsnummer query param" }
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }.build().apply {
        VedtakRiver(this, vedtakDao)
        start()
    }
}
