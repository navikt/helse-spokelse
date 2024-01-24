package no.nav.helse.spokelse

import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.Spleis.Companion.slåSammen
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SpøkelsePeriodeSammenslåerTest {

    @Test
    fun `Sammenhengende perioder hos samme arbeidsgiver med samme grad blir slått sammen til én periode`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, "111111111", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, "111111111", setOf("4")),
        )

        assertEquals(
            listOf(SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, "111111111", setOf("1","2","3","4"))),
            perioder.slåSammen()
        )
    }

    @Test
    fun `Sammenhengende perioder hos samme arbeidsgiver med samme grad blir ikke slått sammen til én periode ved personidentifkatorbytte`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(Personidentifikator("11111111112"), 1.januar, 20.januar, 100, "111111111", setOf("2")),
            SpøkelsePeriode(Personidentifikator("11111111113"), 1.januar, 31.januar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(Personidentifikator("11111111114"), 1.januar, 10.februar, 100, "111111111", setOf("4")),
        )

        assertEquals(perioder, perioder.slåSammen())
    }

    @Test
    fun `Sammenhengende perioder hos forskjellige arbeidsgivere med samme grad blir ikke slått sammen til én periode`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, "111111112", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111113", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, "111111114", setOf("4")),
        )

        assertEquals(perioder, perioder.slåSammen())
    }

    @Test
    fun `Sammenhengende perioder hos samme arbeidsgiver med forskjellig grad blir ikke slått sammen til én periode`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 99, "111111111", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 98, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 97, "111111111", setOf("4")),
        )
        assertEquals(perioder, perioder.slåSammen())
    }

    @Test
    fun `Sammenhengende perioder med innslag av frilanseri med samme grad slår kun sammen periodene på samme organisasjonsnummer`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, null, setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, null, setOf("4")),
        )

        assertEquals(
            listOf(
                SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111111", setOf("1","3")),
                SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, null, setOf("2")),
                SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, null, setOf("4")),
            ),
            perioder.slåSammen()
        )
    }

    private val personidentifikator = Personidentifikator("11111111111")
}
