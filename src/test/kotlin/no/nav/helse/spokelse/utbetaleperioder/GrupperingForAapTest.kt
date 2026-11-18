package no.nav.helse.spokelse.utbetaleperioder

import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.januar
import no.nav.helse.spokelse.mars
import no.nav.helse.spokelse.utbetalteperioder.GroupBy
import no.nav.helse.spokelse.utbetalteperioder.Gruppering
import no.nav.helse.spokelse.utbetalteperioder.tilApiUtbetaltePerioderMedGrad
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import tools.jackson.module.kotlin.jacksonObjectMapper

internal class GrupperingForAapTest {
    @Test
    fun `Et planke oppslag`() {
        val perioder =
            listOf(
                SpøkelsePeriode(personidentifikator, 1.januar, 10.januar, 100, "111111111", setOf("1")),
                SpøkelsePeriode(personidentifikator, 1.januar, 20.januar, 100, "111111111", setOf("2")),
                SpøkelsePeriode(personidentifikator, 1.januar, 31.januar, 100, "111111111", setOf("3")),
                SpøkelsePeriode(personidentifikator, 1.januar, 10.februar, 100, "111111111", setOf("4")),
                SpøkelsePeriode(personidentifikator, 1.mars, 31.mars, 50, "111111111", setOf("5")),
            )

        @Language("JSON")
        val forventet = """
            {
              "utbetaltePerioder": [
                { "fom": "2018-01-01", "tom": "2018-02-10", "grad": 100 },
                { "fom": "2018-03-01", "tom": "2018-03-31", "grad": 50 }
              ]
            }
        """

        assertEquals(forventet, perioder)
    }

    private val personidentifikator = Personidentifikator("11111111111")

    private val AapGruppering = setOf(GroupBy.grad)

    private fun assertEquals(
        forventet: String,
        perioder: List<SpøkelsePeriode>,
    ) {
        // Bare stokker periodene med en partalls-tag som Spleis og oddetall som Infotrygd. Ettersom det ikke grupperes på kilde skal det ikke ha noe å si
        val (spleis, infotrygd) = perioder.partition { it.tags.mapNotNull { tag -> tag.toIntOrNull() }.any { int -> int % 2 == 0 } }
        val faktisk = jacksonObjectMapper().writeValueAsString(Gruppering(AapGruppering, infotrygd, spleis).gruppér().tilApiUtbetaltePerioderMedGrad())
        JSONAssert.assertEquals(forventet, faktisk, JSONCompareMode.NON_EXTENSIBLE)
    }
}
