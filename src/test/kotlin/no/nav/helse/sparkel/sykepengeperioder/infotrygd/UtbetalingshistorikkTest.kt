package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

internal class UtbetalingshistorikkTest {

    @Test
    internal fun utbetalingshistorikk() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingshistorikk = Utbetalingshistorikk(json["sykmeldingsperioder"][1])

        assertEquals(LocalDate.of(2018, 12, 3), utbetalingshistorikk.fom)
        assertEquals(LocalDate.of(2019, 4, 11), utbetalingshistorikk.tom)
        assertEquals("050", utbetalingshistorikk.grad)
        assertNotNull(utbetalingshistorikk.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk.utbetalteSykeperioder)
        assertEquals(1, utbetalingshistorikk.ukjentePerioder.size)
    }

    @Test
    internal fun `utbetalingshistorikk med manglende fom og tom i utbetalingslisten`() {
        val json = readJson("infotrygdResponseMissingFomAndTom.json")
        val utbetalingshistorikk = Utbetalingshistorikk(json["sykmeldingsperioder"][1])

        assertEquals(LocalDate.of(2018, 12, 3), utbetalingshistorikk.fom)
        assertEquals(LocalDate.of(2019, 4, 11), utbetalingshistorikk.tom)
        assertEquals("050", utbetalingshistorikk.grad)
        assertNotNull(utbetalingshistorikk.inntektsopplysninger)
        assertNotNull(utbetalingshistorikk.utbetalteSykeperioder)
        assertEquals(3, utbetalingshistorikk.ukjentePerioder.size)
    }

    @Test
    internal fun `parser hele lista med utbetalingshistorikk`() {
        val json = readJson("infotrygdResponse.json")
        val utbetalingshistorikkListe = json["sykmeldingsperioder"].map { Utbetalingshistorikk(it) }
        assertEquals(20, utbetalingshistorikkListe.size)
        assertEquals(13, utbetalingshistorikkListe.flatMap { it.inntektsopplysninger }.size)
    }

    private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())

    fun readJson(fileName: String) = objectMapper.readTree(this::class.java.getResource("/$fileName").readText())
}
