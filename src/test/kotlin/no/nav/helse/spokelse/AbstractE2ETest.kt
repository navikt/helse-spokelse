package no.nav.helse.spokelse

import com.github.navikt.tbd_libs.naisful.test.naisfulTestApp
import com.github.navikt.tbd_libs.signed_jwt_issuer_test.Issuer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.FakeAccessTokenProvider
import no.nav.helse.spokelse.felles.Infotrygd
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.update
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedager
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedagerDao
import no.nav.helse.spokelse.gjenstaendesykedager.SpleisApiClient
import no.nav.helse.spokelse.personoversikt.Personoversikt
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingApi
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingHåndterer
import no.nav.helse.spokelse.utbetalteperioder.UtbetaltePerioder
import no.nav.sykepenger.libs.logging.navngittLogger
import no.nav.sykepenger.libs.postgres.TestDatabase
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.skyscreamer.jsonassert.JSONAssert
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

@TestInstance(PER_CLASS)
internal abstract class AbstractE2ETest {
    protected data class Infotrygdperiode(
        val fom: LocalDate,
        val tom: LocalDate,
        val type: String = "UTBETALING",
        val maksdato: LocalDate? = null,
        val organisasjonsnummer: String? = null,
        val tags: Set<String> = emptySet(),
    )

    protected data class SpleisApiSvar(
        val status: HttpStatusCode,
        val body: String = "",
    )

    protected var spleisApiSvar = SpleisApiSvar(HttpStatusCode.OK, """{"gjenståendeDager": 87, "vurdertTilOgMed": "2023-02-28", "maksdato": "2023-06-29"}""")
    private val spleisApiClient =
        SpleisApiClient(
            httpClient =
                HttpClient(
                    MockEngine {
                        respond(spleisApiSvar.body, spleisApiSvar.status, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            scope = "api://spleis-api",
            accessTokenProvider = FakeAccessTokenProvider(),
            url = "http://spleis-api",
        )

    // Perioder i den falske Infotrygd. Som i sparkel svarer den bare med perioder som overlapper fom-tom i requesten,
    // og bare med utbetalingstypene hvis ikke inkluderAllePeriodetyper er true. null gir 500.
    protected var infotrygdperioder: List<Infotrygdperiode>? = emptyList()
    protected var idag: LocalDate = LocalDate.now()
    private val infotrygd =
        Infotrygd(
            httpClient =
                HttpClient(
                    MockEngine { request ->
                        val perioder = infotrygdperioder ?: return@MockEngine respondError(HttpStatusCode.InternalServerError)
                        val body = jacksonObjectMapper().readTree((request.body as TextContent).text)
                        val fom = LocalDate.parse(body.path("fom").asString())
                        val tom = LocalDate.parse(body.path("tom").asString())
                        val inkluderAllePeriodetyper = body.path("inkluderAllePeriodetyper").asBoolean(false)
                        val personidentifikator = body.path("personidentifikatorer").first().asString()
                        val utbetaltePerioder =
                            perioder
                                .filter { it.fom <= tom && it.tom >= fom }
                                .filter { inkluderAllePeriodetyper || it.type in setOf("UTBETALING", "REDUKSJON_MEDLEM", "ARB_REF", "REDUKSJON_ARB_REF") }
                                .joinToString {
                                    val maksdato = it.maksdato?.let { dato -> "\"$dato\"" } ?: "null"
                                    val organisasjonsnummer = it.organisasjonsnummer?.let { orgnr -> "\"$orgnr\"" } ?: "null"
                                    """{"personidentifikator": "$personidentifikator", "organisasjonsnummer": $organisasjonsnummer, "fom": "${it.fom}", "tom": "${it.tom}", "grad": 100, "type": "${it.type}", "maksdato": $maksdato, "tags": ${it.tags.map { tag -> "\"$tag\"" }}}"""
                                }
                        respond("""{"utbetaltePerioder": [$utbetaltePerioder]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            scope = "api://infotrygd",
            accessTokenProvider = FakeAccessTokenProvider(),
            url = "http://infotrygd",
        )

    private lateinit var testDatabase: TestDatabase
    protected val dataSource: DataSource get() = testDatabase.dataSource
    protected lateinit var dokumentDao: DokumentDao
    protected lateinit var utbetaltDao: UtbetaltDao
    protected lateinit var gamleUtbetalingerDao: GamleUtbetalingerDao
    protected lateinit var lagreVedtakDao: LagreVedtakDao
    protected lateinit var tbdUtbetalingDao: TbdUtbetalingDao
    protected lateinit var gjenståendeSykedagerDao: GjenståendeSykedagerDao
    protected lateinit var tbdUtbetalingHåndterer: TbdUtbetalingHåndterer
    protected lateinit var meterRegistry: PrometheusMeterRegistry
    protected lateinit var booProducer: MockProducer<String, String>
    protected var utviklergruppe: String? = UTVIKLERGRUPPE
    protected var personoversiktV2ErÅpen = true

    private val issuer = Issuer("spkelse_issuer", "spokelse_azure_ad_app_id")
    private val auth by lazy {
        Auth.auth(
            name = "spokelse_issuer",
            clientId = "spokelse_azure_ad_app_id",
            discoveryUrl = issuer.wellKnownUri().toString(),
        )
    }

    @BeforeAll
    fun beforeAll() {
        issuer.start()
    }

    @AfterAll
    fun afterAll() {
        issuer.stop()
    }

    @BeforeEach
    fun setup() {
        testDatabase = databaseContainer.nyTilkobling()
        dokumentDao = DokumentDao(dataSource)
        utbetaltDao = UtbetaltDao(dataSource)
        gamleUtbetalingerDao = GamleUtbetalingerDao(::dataSource)
        lagreVedtakDao = LagreVedtakDao(dataSource)
        tbdUtbetalingDao = TbdUtbetalingDao(::dataSource)
        gjenståendeSykedagerDao = GjenståendeSykedagerDao(::dataSource)
        booProducer = MockProducer(true, null, StringSerializer(), StringSerializer())
        tbdUtbetalingHåndterer = TbdUtbetalingHåndterer(::dataSource, tbdUtbetalingDao, gjenståendeSykedagerDao, gamleUtbetalingerDao, UtbetalingVarsel(booProducer))
        infotrygdperioder = emptyList()
        spleisApiSvar = SpleisApiSvar(HttpStatusCode.OK, """{"gjenståendeDager": 87, "vurdertTilOgMed": "2023-02-28", "maksdato": "2023-06-29"}""")
        idag = LocalDate.now()
        meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        utviklergruppe = UTVIKLERGRUPPE
        personoversiktV2ErÅpen = true
    }

    @AfterEach
    protected fun reset() {
        databaseContainer.droppTilkobling(testDatabase)
    }

    protected fun assertApiRequest(
        path: String,
        httpMethod: String = "POST",
        requestBody: String? = null,
        forventetHttpStatus: Int = 200,
        forventetResponseBody: String? = null,
        forventetContentType: ContentType? = null,
        timeout: Duration = Duration.ofSeconds(5),
        rolle: String?,
        app: String?,
        grupper: List<String> = emptyList(),
        claims: Map<String, String> = emptyMap(),
    ): String {
        var body = ""
        naisfulTestApp(
            testApplicationModule = {
                val tbdUtbetalingApi = TbdUtbetalingApi(tbdUtbetalingDao)
                spokelse(
                    auth = auth,
                    gamleUtbetalingerDao = gamleUtbetalingerDao,
                    tbdUtbetalingApi = tbdUtbetalingApi,
                    utbetaltePerioder =
                        UtbetaltePerioder(
                            infotrygd = infotrygd,
                            tbdUtbetalingApi = tbdUtbetalingApi,
                            gamleUtbetalingerDao = gamleUtbetalingerDao,
                        ),
                    gjenståendeSykedager =
                        GjenståendeSykedager(
                            dao = gjenståendeSykedagerDao,
                            infotrygdperioder = infotrygd,
                            maksdatoberegning = spleisApiClient,
                            meterRegistry = meterRegistry,
                            idag = { idag },
                        ),
                    meterRegistry = meterRegistry,
                    utviklergruppe = utviklergruppe,
                    personoversikt = Personoversikt(infotrygd, tbdUtbetalingApi, gamleUtbetalingerDao).takeIf { personoversiktV2ErÅpen },
                )
            },
            objectMapper = jacksonObjectMapper(),
            meterRegistry = meterRegistry,
        ) {
            Awaitility.await().atMost(timeout).untilAsserted {
                val response =
                    runBlocking {
                        client.request("/$path") {
                            method = HttpMethod.parse(httpMethod)
                            accept(ContentType.Application.Json)
                            if (rolle != null || app != null || grupper.isNotEmpty() || claims.isNotEmpty()) {
                                bearerAuth(
                                    issuer.accessToken {
                                        app?.let { withClaim("azp", it) }
                                        rolle?.let { withArrayClaim("roles", arrayOf(it)) }
                                        if (grupper.isNotEmpty()) withArrayClaim("groups", grupper.toTypedArray())
                                        claims.forEach { (navn, verdi) -> withClaim(navn, verdi) }
                                    },
                                )
                            }
                            if (requestBody != null) {
                                contentType(ContentType.Application.Json)
                            }
                            if (requestBody != null) {
                                setBody(requestBody)
                            }
                        }
                    }
                body = runBlocking { response.bodyAsText() }
                logg.info("Fikk svar tilbake fra /$path", "body" to body)
                assertEquals(HttpStatusCode.fromValue(forventetHttpStatus), response.status)
                forventetContentType?.let { assertEquals(it, response.contentType()?.withoutParameters()) }
                forventetResponseBody?.let {
                    try {
                        JSONAssert.assertEquals(it, body, true)
                    } catch (err: AssertionError) {
                        logg.info("Svaret er ikke som forventet", err, "body" to body, "forventet" to it)
                        throw err
                    }
                }
            }
        }
        return body
    }

    protected fun lagreOldVedtakOgOldUtbetaling(
        fom: LocalDate,
        tom: LocalDate,
        fagsystemId: String,
        fødselsnummer: String,
        organisasjonsnummer: String = "999999999",
    ) {
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        val vedtaksperiodeId = UUID.randomUUID()
        dokumentDao.lagre(vedtaksperiodeId, fagsystemId)
        lagreVedtakDao.save(
            OldVedtak(
                vedtaksperiodeId = vedtaksperiodeId,
                fødselsnummer = fødselsnummer,
                orgnummer = organisasjonsnummer,
                utbetalinger =
                    listOf(
                        OldUtbetaling(
                            fom = fom,
                            tom = tom,
                            grad = 100.0,
                            dagsats = 1,
                            totalbeløp = 1,
                            beløp = 1,
                        ),
                    ),
                opprettet = LocalDateTime.now(),
                forbrukteSykedager = 1,
                gjenståendeSykedager = 1,
                dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding),
            ),
        )
    }

    protected fun lagreVedtakOppdragOgUtbetaling(
        fom: LocalDate,
        tom: LocalDate,
        fagsystemId: String,
        fødselsnummer: String,
        fagområde: String = "SPREF",
        organisasjonsnummer: String = "999999999",
    ) {
        val (sykmelding, søknad, inntektsmelding) = nyeDokumenter()
        utbetaltDao.opprett(
            Vedtak(
                hendelseId = UUID.randomUUID(),
                fødselsnummer = fødselsnummer,
                orgnummer = organisasjonsnummer,
                dokumenter = Dokumenter(sykmelding, søknad, inntektsmelding),
                oppdrag = listOf(Vedtak.Oppdrag("mottaker", fagområde, fagsystemId, 1, listOf(Vedtak.Oppdrag.Utbetalingslinje(fom, tom, 1, 1, 100.0, 1)))),
                fom = fom,
                tom = tom,
                forbrukteSykedager = 1,
                gjenståendeSykedager = 1,
                opprettet = LocalDateTime.now(),
            ),
        )
    }

    protected fun lagreGammelUtbetaling(
        fom: LocalDate,
        tom: LocalDate,
        fagsystemId: String,
        fødselsnummer: String,
        fagområde: String = "SPREF",
        organisasjonsnummer: String = "999999999",
    ) {
        sessionOf(dataSource).use { session ->
            asSQL(
                """
                INSERT INTO gamle_utbetalinger (fodselsnummer, orgnummer, opprettet, fom, tom, grad, fagsystem_id, fagomrade, gjenstaende_sykedager)
                VALUES (:fodselsnummer, :orgnummer, now(), :fom, :tom, 100.0, :fagsystemId, :fagomrade, 1)
                """.trimIndent(),
                "fodselsnummer" to fødselsnummer,
                "orgnummer" to organisasjonsnummer,
                "fom" to fom,
                "tom" to tom,
                "fagsystemId" to fagsystemId,
                "fagomrade" to fagområde,
            ).update(session)
        }
    }

    protected fun nyeDokumenter(): Triple<Hendelse, Hendelse, Hendelse> {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
        dokumentDao.opprett(sykmelding)
        dokumentDao.opprett(søknad)
        dokumentDao.opprett(inntektsmelding)
        return Triple(sykmelding, søknad, inntektsmelding)
    }

    internal companion object {
        private val logg = navngittLogger(AbstractE2ETest::class.java.name)
        internal const val UTVIKLERGRUPPE = "utviklergruppe"

        internal fun oppdrag(
            fødselsnummer: String,
            fagsystemId: String,
            fagområde: String,
            fom: LocalDate,
            tom: LocalDate,
        ) = Vedtak.Oppdrag(
            mottaker = fødselsnummer,
            fagområde = fagområde,
            fagsystemId = fagsystemId,
            totalbeløp = 0,
            utbetalingslinjer =
                listOf(
                    Vedtak.Oppdrag.Utbetalingslinje(
                        fom = fom,
                        tom = tom,
                        dagsats = 123,
                        beløp = 321,
                        grad = 70.0,
                        sykedager = 248,
                    ),
                ),
        )
    }
}
