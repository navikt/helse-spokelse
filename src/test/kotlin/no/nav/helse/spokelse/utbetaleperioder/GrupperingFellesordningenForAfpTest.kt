package no.nav.helse.spokelse.utbetaleperioder

import no.nav.helse.spokelse.april
import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.januar
import no.nav.helse.spokelse.mars
import no.nav.helse.spokelse.utbetalteperioder.GroupBy
import no.nav.helse.spokelse.utbetalteperioder.Gruppering
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

internal class GrupperingFellesordningenForAfpTest {

    @Test
    fun `Sammenhengende perioder hos samme arbeidsgiver med samme grad blir slått sammen til én periode`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, "111111111", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, "111111111", setOf("4")),
        )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-02-10", "grad": 100, "tags": ["1", "2", "3", "4"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    @Test
    fun `Sammenhengende perioder hos samme arbeidsgiver med samme grad blir ikke slått sammen til én periode ved personidentifkatorbytte`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(Personidentifikator("11111111112"), 1.januar, 20.januar, 100, "111111111", setOf("2")),
            SpøkelsePeriode(Personidentifikator("11111111113"), 1.januar, 31.januar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(Personidentifikator("11111111114"), 1.januar, 10.februar, 100, "111111111", setOf("4")),
        )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-10", "grad": 100, "tags": ["1"] },
                { "personidentifikator": "11111111112", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-20", "grad": 100, "tags": ["2"] },
                { "personidentifikator": "11111111113", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-31", "grad": 100, "tags": ["3"] },
                { "personidentifikator": "11111111114", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-02-10", "grad": 100, "tags": ["4"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    @Test
    fun `Sammenhengende perioder hos forskjellige arbeidsgivere med samme grad blir ikke slått sammen til én periode`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, "111111112", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111113", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, "111111114", setOf("4")),
        )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-10", "grad": 100, "tags": ["1"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111112", "fom": "2018-01-01", "tom": "2018-01-20", "grad": 100, "tags": ["2"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111113", "fom": "2018-01-01", "tom": "2018-01-31", "grad": 100, "tags": ["3"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111114", "fom": "2018-01-01", "tom": "2018-02-10", "grad": 100, "tags": ["4"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    @Test
    fun `Sammenhengende perioder hos samme arbeidsgiver med forskjellig grad blir ikke slått sammen til én periode`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 99, "111111111", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 98, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 97, "111111111", setOf("4")),
        )
        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-10", "grad": 100, "tags": ["1"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-20", "grad": 99, "tags": ["2"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-31", "grad": 98, "tags": ["3"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-02-10", "grad": 97, "tags": ["4"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    @Test
    fun `Sammenhengende perioder med innslag av frilanseri med samme grad slår kun sammen periodene på samme organisasjonsnummer`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, null, setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, null, setOf("4")),
        )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-01", "tom": "2018-01-31", "grad": 100, "tags": ["1", "3"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": null, "fom": "2018-01-01", "tom": "2018-01-20", "grad": 100, "tags": ["2"] },
                { "personidentifikator": "11111111111", "organisasjonsnummer": null, "fom": "2018-01-01", "tom": "2018-02-10", "grad": 100, "tags": ["4"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    @Test
    fun `Kant-i-kant perioder på samme arbeidsgiver og samme grad slås sammen til én`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 17.januar, 31.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 1.mars, 31.mars, 100, "111111111", setOf("2")),
            SpøkelsePeriode(personidentifikator, 1.februar, 28.februar, 100, "111111111", setOf("3")),
            SpøkelsePeriode(personidentifikator, 1.mars, 31.mars, 100, "111111111", setOf("4")),
            SpøkelsePeriode(personidentifikator, 1.april, 30.april, 100, "111111111", setOf("5"))
        )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-17", "tom": "2018-04-30", "grad": 100, "tags": ["1", "2", "3", "4", "5"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    @Test
    fun `Delvis overlappende perioder slås sammen til én`() {
        val perioder = listOf(
            SpøkelsePeriode(personidentifikator, 17.januar, 31.januar, 100, "111111111", setOf("1")),
            SpøkelsePeriode(personidentifikator, 20.januar, 31.mars, 100, "111111111", setOf("2")),
        )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "fom": "2018-01-17", "tom": "2018-03-31", "grad": 100, "tags": ["1", "2"] }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    private val personidentifikator = Personidentifikator("11111111111")

    private val FellesordningenForAfpGroupBy = setOf(GroupBy.organisasjonsnummer, GroupBy.grad, GroupBy.personidentifikator)
    private fun assertEquals(forventet: String, perioder: List<SpøkelsePeriode>) {
        // Bare stokker periodene med en partalls-tag som Spleis og oddetall som Infotrygd. Ettersom det ikke grupperes på kilde skal det ikke ha noe å si
        val (spleis, infotrygd) = perioder.partition { it.tags.mapNotNull { tag-> tag.toIntOrNull() }.any { int -> int % 2 == 0 }}
        val faktisk = Gruppering(FellesordningenForAfpGroupBy, infotrygd, spleis).gruppér()
        JSONAssert.assertEquals(forventet, faktisk, JSONCompareMode.NON_EXTENSIBLE)
    }
}
