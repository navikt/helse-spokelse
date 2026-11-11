package no.nav.helse.spokelse.gjenstaendesykedager

import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.spokelse.databaseContainer
import no.nav.helse.spokelse.felles.Infotrygdperiode
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.gamleutbetalinger.GamleUtbetalingerDao
import no.nav.helse.spokelse.gjenstaendesykedager.GjenståendeSykedager.Resultat
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingDao
import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingHåndterer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class GjenståendeSykedagerTest {
    private lateinit var testDataSource: TestDataSource
    private lateinit var tbdUtbetalingHåndterer: TbdUtbetalingHåndterer
    private lateinit var gjenståendeSykedager: GjenståendeSykedager
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var infotrygd: FalskInfotrygd
    private lateinit var spleisApi: FalskSpleisApi
    private var idag = LocalDate.parse("2023-03-01")

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
        val dataSource = { testDataSource.ds }
        val tbdUtbetalingDao = TbdUtbetalingDao(dataSource)
        val gjenståendeSykedagerDao = GjenståendeSykedagerDao(dataSource)
        tbdUtbetalingHåndterer = TbdUtbetalingHåndterer(tbdUtbetalingDao, gjenståendeSykedagerDao, observers = listOf(tbdUtbetalingDao, GamleUtbetalingerDao(dataSource)))
        meterRegistry = SimpleMeterRegistry()
        infotrygd = FalskInfotrygd()
        spleisApi = FalskSpleisApi()
        gjenståendeSykedager = GjenståendeSykedager(gjenståendeSykedagerDao, infotrygd, spleisApi, meterRegistry, idag = { idag })
    }

    @AfterEach
    fun reset() {
        databaseContainer.droppTilkobling(testDataSource)
    }

    @Test
    fun `senere utbetaling i Infotrygd gir SISTE_UTBETALING_I_INFOTRYGD med tallet fra spleis-api`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygd.perioder = listOf(utbetaling("2023-01-01", "2023-02-28"))
        spleisApi.beregning = Maksdatoberegning.Beregning(120, LocalDate.parse("2023-02-28"), LocalDate.parse("2023-08-15"))
        assertResultat(Resultat.sisteUtbetalingIInfotrygd(120, LocalDate.parse("2023-02-28")))
    }

    @Test
    fun `henter alle periodene i Infotrygd fra 2000-01-01 til i dag og sender dem til spleis-api`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygd.perioder =
            listOf(
                utbetaling("1999-12-20", "2000-01-10", organisasjonsnummer = "888888888"),
                utbetaling("2022-06-01", "2022-06-30", type = "FERIE"),
                utbetaling("2023-02-01", "2023-02-28", type = "ARB_REF", organisasjonsnummer = "999263550", maksdato = "2023-12-01"),
            )
        assertResultat(sisteUtbetalingIInfotrygd)
        assertEquals(listOf(LocalDate.parse("2000-01-01") to idag), infotrygd.forespørsler)
        assertEquals(listOf(infotrygd.perioder), spleisApi.mottattePerioder)
    }

    @Test
    fun `spleis-api kalles ikke når Infotrygd ikke har noen senere utbetaling`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        infotrygd.perioder = listOf(utbetaling("2022-01-01", "2023-01-31"))
        assertResultat(vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
        assertEquals(emptyList<Any>(), spleisApi.mottattePerioder)
    }

    @Test
    fun `eldre utbetaling i Infotrygd påvirker ikke svaret`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        infotrygd.perioder = listOf(utbetaling("2022-01-01", "2023-01-31"))
        assertResultat(vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
    }

    @Test
    fun `182 dager etter siste vedtak gir ny rett, 181 dager gjør det ikke`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"), gjenståendeSykedager = 221)
        idag = LocalDate.parse("2023-07-31")
        assertResultat(vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
        idag = LocalDate.parse("2023-08-01")
        assertResultat(Resultat.ingenSykepengerSiste26Uker)
    }

    @Test
    fun `182 dager etter siste utbetaling i Infotrygd gir ny rett, 181 dager gjør det ikke`() {
        infotrygd.perioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"))
        idag = LocalDate.parse("2027-04-29")
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
        idag = LocalDate.parse("2027-04-30")
        assertResultat(Resultat.ingenSykepengerSiste26Uker)
    }

    @Test
    fun `ny rett regnes fra det som er senest av siste vedtak og siste utbetaling i Infotrygd`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygd.perioder = listOf(utbetaling("2023-02-01", "2023-02-28"))
        idag = LocalDate.parse("2023-08-01")
        assertResultat(sisteUtbetalingIInfotrygd)
    }

    @Test
    fun `ferie i Infotrygd utsetter ikke ny rett`() {
        infotrygd.perioder =
            listOf(
                utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"),
                utbetaling("2026-10-31", "2026-11-30", type = "FERIE"),
            )
        idag = LocalDate.parse("2027-04-30")
        assertResultat(Resultat.ingenSykepengerSiste26Uker)
    }

    @Test
    fun `minst 182 dager opphold etter siste vedtak gir Infotrygds maksdato`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-10-15")
        infotrygd.perioder = listOf(utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"))
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 23, vurdertTilOgMed = "2025-09-30"))
        infotrygd.perioder = listOf(utbetaling("2025-09-01", "2025-09-30"))
        assertResultat(Resultat.manglerMaksdatoIInfotrygd)
    }

    @Test
    fun `opphold på 182 dager etter siste vedtak gir Infotrygds maksdato, 181 dager gjør det ikke`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-08-31")
        infotrygd.perioder = listOf(utbetaling("2025-08-01", "2025-08-29", maksdato = "2025-09-05"))
        assertResultat(sisteUtbetalingIInfotrygd)
        infotrygd.perioder = listOf(utbetaling("2025-08-02", "2025-08-29", maksdato = "2025-09-05"))
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 5, vurdertTilOgMed = "2025-08-29"))
    }

    @Test
    fun `opphold på minst 182 dager mellom to utbetalinger i Infotrygd etter siste vedtak gir Infotrygds maksdato`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-10-15")
        infotrygd.perioder =
            listOf(
                utbetaling("2025-02-01", "2025-02-28"),
                utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"),
            )
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 23, vurdertTilOgMed = "2025-09-30"))
    }

    @Test
    fun `ferie i Infotrygd bryter ikke oppholdet etter siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2025-10-15")
        infotrygd.perioder =
            listOf(
                utbetaling("2025-05-01", "2025-05-31", type = "FERIE"),
                utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"),
            )
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 23, vurdertTilOgMed = "2025-09-30"))
    }

    @Test
    fun `ny rett sjekkes før oppholdet etter siste vedtak`() {
        lagreVedtak(tom = LocalDate.parse("2025-01-31"))
        idag = LocalDate.parse("2026-03-31")
        infotrygd.perioder = listOf(utbetaling("2025-09-01", "2025-09-30", maksdato = "2025-10-31"))
        assertResultat(Resultat.ingenSykepengerSiste26Uker)
    }

    @Test
    fun `bare utbetalinger i Infotrygd gir gjenstående dager fra Infotrygds maksdato`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygd.perioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"))
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
    }

    @Test
    fun `maksdato samme dag som eller før siste utbetalte dag gir 0`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygd.perioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-10-30"))
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 0, vurdertTilOgMed = "2026-10-30"))
        infotrygd.perioder = listOf(utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-10-15"))
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 0, vurdertTilOgMed = "2026-10-30"))
    }

    @Test
    fun `mangler perioden med siste utbetaling maksdato, gir det MANGLER_MAKSDATO_I_INFOTRYGD`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygd.perioder =
            listOf(
                utbetaling("2026-09-01", "2026-09-30", maksdato = "2027-06-01"),
                utbetaling("2026-10-01", "2026-10-30"),
            )
        assertResultat(Resultat.manglerMaksdatoIInfotrygd)
    }

    @Test
    fun `ferie og andre typer enn utbetalinger er ikke siste utbetaling`() {
        idag = LocalDate.parse("2026-11-10")
        infotrygd.perioder =
            listOf(
                utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13"),
                utbetaling("2026-10-31", "2026-11-05", type = "FERIE"),
                utbetaling("2026-10-31", "2026-11-06", type = "TILBAKEFØRT"),
            )
        assertResultat(maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
    }

    @Test
    fun `alle utbetalingstypene kan være siste utbetaling`() {
        idag = LocalDate.parse("2026-11-10")
        listOf("UTBETALING", "REDUKSJON_MEDLEM", "ARB_REF", "REDUKSJON_ARB_REF").forEach { type ->
            infotrygd.perioder =
                listOf(
                    utbetaling("2026-09-01", "2026-09-30", maksdato = "2026-12-31"),
                    utbetaling("2026-10-01", "2026-10-30", maksdato = "2026-11-13", type = type),
                )
            assertResultat(maksdatoIInfotrygd(gjenståendeDager = 10, vurdertTilOgMed = "2026-10-30"))
        }
    }

    @Test
    fun `sammenligner tallet fra spleis-api med Infotrygds maksdato uten å endre resultatet`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygd.perioder = listOf(utbetaling("2023-02-01", "2023-02-28", maksdato = "2023-06-29"))
        assertResultat(sisteUtbetalingIInfotrygd)
        infotrygd.perioder = listOf(utbetaling("2023-02-01", "2023-02-28", maksdato = "2023-06-30"))
        assertResultat(sisteUtbetalingIInfotrygd)
        infotrygd.perioder = listOf(utbetaling("2023-02-01", "2023-02-28", maksdato = "2023-06-26"))
        assertResultat(sisteUtbetalingIInfotrygd)
        infotrygd.perioder = listOf(utbetaling("2023-02-01", "2023-02-28"))
        assertResultat(sisteUtbetalingIInfotrygd)

        mapOf("LIK" to 1.0, "ULIK" to 2.0, "MANGLER_MAKSDATO" to 1.0).forEach { (utfall, antall) ->
            assertEquals(antall, sammenligninger(utfall), "utfall=$utfall")
        }
    }

    @Test
    fun `sammenligner ikke med Infotrygds maksdato når tallet ikke kommer fra spleis-api`() {
        lagreVedtak(tom = LocalDate.parse("2023-01-31"))
        infotrygd.perioder = listOf(utbetaling("2023-01-01", "2023-01-31", maksdato = "2023-06-29"))
        assertResultat(vedtakINyLøsning(gjenståendeDager = 221, vurdertTilOgMed = "2023-01-31"))
        listOf("LIK", "ULIK", "MANGLER_MAKSDATO").forEach { utfall -> assertEquals(null, sammenligninger(utfall), "utfall=$utfall") }
    }

    private fun sammenligninger(utfall: String) =
        meterRegistry
            .find("gjenstaende_sykedager_sammenligning_med_infotrygd")
            .tag("utfall", utfall)
            .counter()
            ?.count()

    private fun lagreVedtak(
        tom: LocalDate,
        gjenståendeSykedager: Int = 221,
    ) = tbdUtbetalingHåndterer.lagreVedtak(tom = tom, gjenståendeSykedager = gjenståendeSykedager)

    private fun assertResultat(forventet: Resultat) {
        assertEquals(forventet, runBlocking { gjenståendeSykedager.hent(Personidentifikator(FØDSELSNUMMER)) })
    }

    private fun vedtakINyLøsning(
        gjenståendeDager: Int,
        vurdertTilOgMed: String,
    ) = Resultat.vedtakINyLøsning(gjenståendeDager, LocalDate.parse(vurdertTilOgMed))

    private fun maksdatoIInfotrygd(
        gjenståendeDager: Int,
        vurdertTilOgMed: String,
    ) = Resultat.maksdatoIInfotrygd(gjenståendeDager, LocalDate.parse(vurdertTilOgMed))

    private fun utbetaling(
        fom: String,
        tom: String,
        maksdato: String? = null,
        type: String = "UTBETALING",
        organisasjonsnummer: String? = null,
    ) = Infotrygdperiode(LocalDate.parse(fom), LocalDate.parse(tom), type, maksdato?.let(LocalDate::parse), organisasjonsnummer)

    private class FalskInfotrygd : Infotrygdperioder {
        var perioder: List<Infotrygdperiode> = emptyList()
        val forespørsler = mutableListOf<Pair<LocalDate, LocalDate>>()

        override suspend fun hentAllePeriodetyper(
            personidentifikator: Personidentifikator,
            fom: LocalDate,
            tom: LocalDate,
        ): List<Infotrygdperiode> {
            forespørsler.add(fom to tom)
            return perioder
        }
    }

    private class FalskSpleisApi : Maksdatoberegning {
        var beregning = Maksdatoberegning.Beregning(87, LocalDate.parse("2023-02-28"), LocalDate.parse("2023-06-29"))
        val mottattePerioder = mutableListOf<List<Infotrygdperiode>>()

        override suspend fun beregnGjenståendeSykedager(
            personidentifikator: Personidentifikator,
            infotrygdperioder: List<Infotrygdperiode>,
        ): Maksdatoberegning.Beregning {
            mottattePerioder.add(infotrygdperioder)
            return beregning
        }
    }

    private companion object {
        private val sisteUtbetalingIInfotrygd = Resultat.sisteUtbetalingIInfotrygd(87, LocalDate.parse("2023-02-28"))
    }
}
