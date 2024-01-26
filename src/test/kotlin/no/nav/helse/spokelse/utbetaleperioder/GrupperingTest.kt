package no.nav.helse.spokelse.utbetaleperioder

import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.januar
import no.nav.helse.spokelse.utbetalteperioder.GroupBy
import no.nav.helse.spokelse.utbetalteperioder.Gruppering
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class GrupperingTest {

    @Test
    fun `Gir forskjellig response avhengig av hva det grupperes på`() {
        val infotrygd = listOf(
            SpøkelsePeriode(Personidentifikator("11111111111"), 1.januar, 10.januar, 100, "111111111", setOf("IT1")),
            SpøkelsePeriode(Personidentifikator("11111111112"), 11.januar, 20.januar, 99, "111111112", setOf("IT2")),
        )
        val spleis = listOf(
            SpøkelsePeriode(Personidentifikator("11111111113"), 19.januar, 31.januar, 98, "111111113", setOf("S1")),
            SpøkelsePeriode(Personidentifikator("11111111114"), 1.februar, 10.februar, 97, "111111114", setOf("S2")),
        )

        @Language("JSON")
        val forventetIngenGruppering = """
        {
          "utbetaltePerioder": [
            { "fom": "2018-01-01", "tom": "2018-02-10", "tags": ["IT1", "IT2", "S1", "S2"] }
          ]
        }
        """
        assertJsonEquals(forventetIngenGruppering, Gruppering(groupBy = emptySet(), infotrygd = infotrygd, spleis = spleis).gruppér())

        @Language("JSON")
        val forventetKildeGruppering = """
        {
          "utbetaltePerioder": [
            { "fom": "2018-01-01", "tom": "2018-01-20", "tags": ["IT1", "IT2"]}, {"fom": "2018-01-19", "tom": "2018-02-10", "tags": ["S1", "S2"] }
          ]
        }
        """
        assertJsonEquals(forventetKildeGruppering, Gruppering(groupBy = setOf(GroupBy.kilde), infotrygd = infotrygd, spleis = spleis).gruppér())

        @Language("JSON")
        val forventetPersonidentifikatorGruppering = """
            {
              "utbetaltePerioder": [
                { "personidentifikator": "11111111111", "fom": "2018-01-01", "tom": "2018-01-10", "tags": ["IT1"] },
                { "personidentifikator": "11111111112", "fom": "2018-01-11", "tom": "2018-01-20", "tags": ["IT2"] },
                { "personidentifikator": "11111111113", "fom": "2018-01-19", "tom": "2018-01-31", "tags": ["S1"] },
                { "personidentifikator": "11111111114", "fom": "2018-02-01", "tom": "2018-02-10", "tags": ["S2"] }
              ]
            }
        """
        assertJsonEquals(forventetPersonidentifikatorGruppering, Gruppering(groupBy = setOf(GroupBy.personidentifikator), infotrygd = infotrygd, spleis = spleis).gruppér())

        @Language("JSON")
        val forventetAlleGrupperinger = """
        {
          "utbetaltePerioder": [
            { "personidentifikator": "11111111111", "organisasjonsnummer": "111111111", "grad": 100, "fom": "2018-01-01", "tom": "2018-01-10", "tags": ["IT1"] },
            { "personidentifikator": "11111111112", "organisasjonsnummer": "111111112", "grad": 99, "fom": "2018-01-11", "tom": "2018-01-20", "tags": ["IT2"] },
            { "personidentifikator": "11111111113", "organisasjonsnummer": "111111113", "grad": 98, "fom": "2018-01-19", "tom": "2018-01-31", "tags": ["S1"] },
            { "personidentifikator": "11111111114", "organisasjonsnummer": "111111114", "grad": 97, "fom": "2018-02-01", "tom": "2018-02-10", "tags": ["S2"] }
          ]
        }
        """
        assertJsonEquals(forventetAlleGrupperinger, Gruppering(groupBy = GroupBy.values().toSet(), infotrygd = infotrygd, spleis = spleis).gruppér())
    }

    private fun assertJsonEquals(forventet: String, faktisk: String) = JSONAssert.assertEquals(forventet, faktisk, true)
}
