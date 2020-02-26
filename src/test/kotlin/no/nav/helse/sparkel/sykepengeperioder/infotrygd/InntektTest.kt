package no.nav.helse.sparkel.sykepengeperioder.infotrygd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InntektTest {

    @Test
    fun `kan regne om daglig inntekt`() {
        assertEquals(10833, Inntektsopplysninger.PeriodeKode.Daglig.omregn(500.toBigDecimal()))
    }

    @Test
    fun `kan regne om ukentlig inntekt`() {
        assertEquals(21667, Inntektsopplysninger.PeriodeKode.Ukentlig.omregn(5000.toBigDecimal()))
    }

    @Test
    fun `kan regne om biukentlig inntekt`() {
        assertEquals(21667, Inntektsopplysninger.PeriodeKode.Biukentlig.omregn(10000.toBigDecimal()))
    }

    @Test
    fun `kan regne om måndelig inntekt`() {
        assertEquals(39000, Inntektsopplysninger.PeriodeKode.Månedlig.omregn(39000.toBigDecimal()))
    }

    @Test
    fun `kan regne om årlig inntekt`() {
        assertEquals(43333, Inntektsopplysninger.PeriodeKode.Årlig.omregn(520000.toBigDecimal()))
    }

    @Test
    fun `kan regne om skjønnsmessig fastsatt inntekt`() {
        assertEquals(32083, Inntektsopplysninger.PeriodeKode.SkjønnsmessigFastsatt.omregn(385000.toBigDecimal()))
    }

    @Test
    fun `kan regne om premiegrunnlag`() {
        assertEquals(34667, Inntektsopplysninger.PeriodeKode.Premiegrunnlag.omregn(416000.toBigDecimal()))
    }

    @Test
    fun `avrunding etter half-up prinsippet`() {
        assertEquals(41667, Inntektsopplysninger.PeriodeKode.Årlig.omregn(500000.toBigDecimal()))
        assertEquals(26017, Inntektsopplysninger.PeriodeKode.Ukentlig.omregn(6004.toBigDecimal()))
        assertEquals(26745, Inntektsopplysninger.PeriodeKode.Daglig.omregn(1234.4.toBigDecimal()))
    }

    @Test
    fun `kaster feil ved andre periodetyper`() {
        assertThrows<IllegalArgumentException> { Inntektsopplysninger.PeriodeKode.verdiFraKode("I") }
    }
}