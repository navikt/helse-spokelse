package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.utbetaling
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

internal class UtbetalingMappingTest {

    @Test
    fun `utbetaling_utbetalt begge oppdrag`() {
        val meldingSendt = nå()
        val utbetaling = jsonBeggeOppdrag.utbetaling(meldingSendt)
        val forventet = Utbetaling(
            fødselsnummer = "12345678901",
            korrelasjonsId = UUID.fromString("a43696c7-e824-4140-b8a7-348efe7128cc"),
            gjenståendeSykedager = 178,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "XI2MMEZAJZBVJL2E4K7UM4BQBY",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(fom = LocalDate.parse("2018-01-01"), tom = LocalDate.parse("2018-01-31"), grad = 66.5),
                    Utbetalingslinje(fom = LocalDate.parse("2018-02-01"), tom = LocalDate.parse("2018-02-28"), grad = 50.0)
                )
            ),
            personOppdrag = Oppdrag(
                fagsystemId = "L52NYV4KE5BEPILU4L2ERGAVYU",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(fom = LocalDate.parse("2018-02-01"), tom = LocalDate.parse("2018-02-28"), grad = 50.0)
                )
            ),
            sistUtbetalt = meldingSendt
        )
        assertEquals(forventet, utbetaling)
    }

    @Test
    fun `utbetaling_utbetalt ingen oppdrag`() {
        assertThrows<IllegalArgumentException> { jsonIngenOppdrag.utbetaling(nå()) }
    }

    @Test
    fun `utbetaling_utbetalt kun arbeidsgiveroppdrag`() {
        val meldingSendt = nå()
        val utbetaling = jsonKunArbeidsgiveroppdrag.utbetaling(meldingSendt)
        val forventet = Utbetaling(
            fødselsnummer = "12345678901",
            korrelasjonsId = UUID.fromString("a43696c7-e824-4140-b8a7-348efe7128cc"),
            gjenståendeSykedager = 178,
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "XI2MMEZAJZBVJL2E4K7UM4BQBY",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(fom = LocalDate.parse("2018-01-01"), tom = LocalDate.parse("2018-01-31"), grad = 66.5),
                    Utbetalingslinje(fom = LocalDate.parse("2018-02-01"), tom = LocalDate.parse("2018-02-28"), grad = 50.0)
                )
            ),
            personOppdrag = null,
            sistUtbetalt = meldingSendt
        )
        assertEquals(forventet, utbetaling)
    }

    @Test
    fun `utbetaling_utbetalt kun personoppdrag`() {
        val meldingSendt = nå()
        val utbetaling = jsonKunPersonoppdrag.utbetaling(meldingSendt)
        val forventet = Utbetaling(
            fødselsnummer = "12345678901",
            korrelasjonsId = UUID.fromString("a43696c7-e824-4140-b8a7-348efe7128cc"),
            gjenståendeSykedager = 178,
            arbeidsgiverOppdrag = null,
            personOppdrag = Oppdrag(
                fagsystemId = "L52NYV4KE5BEPILU4L2ERGAVYU",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(fom = LocalDate.parse("2018-02-01"), tom = LocalDate.parse("2018-02-28"), grad = 50.0)
                )
            ),
            sistUtbetalt = meldingSendt
        )
        assertEquals(forventet, utbetaling)
    }

    private companion object {
        private val jackson = jacksonObjectMapper()

        @Language("JSON")
        private val jsonBeggeOppdrag = """
        {
          "event": "utbetaling_utbetalt",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "12345678901",
          "aktørId": "<string>",
          "organisasjonsnummer": "999263550",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "forbrukteSykedager": 70,
          "gjenståendeSykedager": 178,
          "stønadsdager": 70,
          "automatiskBehandling": true,
          "type": "UTBETALING",
          "antallVedtak": 1,
          "foreløpigBeregnetSluttPåSykepenger": "2018-12-31",
          "utbetalingsdager": [
            {
              "dato": "2018-01-01",
              "begrunnelser": [
                "SykepengedagerOppbrukt"
              ],
              "type": "AvvistDag"
            }
          ],
          "arbeidsgiverOppdrag": {
            "mottaker": "999263550",
            "fagområde": "SPREF",
            "fagsystemId": "XI2MMEZAJZBVJL2E4K7UM4BQBY",
            "nettoBeløp": 7155,
            "stønadsdager": 20,
            "fom": "2018-01-01",
            "tom": "2018-01-31",
            "utbetalingslinjer": [
              {
                "fom": "2018-01-01",
                "tom": "2018-01-31",
                "dagsats": 1431,
                "totalbeløp": 14310,
                "grad": 66.5,
                "stønadsdager": 20
              },
              {
                "fom": "2018-02-01",
                "tom": "2018-02-28",
                "dagsats": 1431,
                "totalbeløp": 14310,
                "grad": 50.0,
                "stønadsdager": 20
              }
            ]
          },
          "personOppdrag": {
            "mottaker": "<string>",
            "fagområde": "SP",
            "fagsystemId": "L52NYV4KE5BEPILU4L2ERGAVYU",
            "nettoBeløp": 7155,
            "stønadsdager": 20,
            "fom": "2018-01-01",
            "tom": "2018-01-31",
            "utbetalingslinjer": [
              {
                "fom": "2018-02-01",
                "tom": "2018-02-28",
                "dagsats": 1431,
                "totalbeløp": 14310,
                "grad": 50.0,
                "stønadsdager": 20
              }
            ]
          }
        }
        """.let { jackson.readTree(it) }

        @Language("JSON")
        private val jsonIngenOppdrag = """
        {
          "event": "utbetaling_utbetalt",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "12345678901",
          "aktørId": "<string>",
          "organisasjonsnummer": "999263550",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "forbrukteSykedager": 70,
          "gjenståendeSykedager": 178,
          "stønadsdager": 70,
          "automatiskBehandling": true,
          "type": "UTBETALING",
          "antallVedtak": 1,
          "foreløpigBeregnetSluttPåSykepenger": "2018-12-31",
          "utbetalingsdager": [
            {
              "dato": "2018-01-01",
              "begrunnelser": [
                "SykepengedagerOppbrukt"
              ],
              "type": "AvvistDag"
            }
          ],
          "arbeidsgiverOppdrag": null,
          "personOppdrag": {
            "mottaker": "<string>",
            "fagområde": "SP",
            "fagsystemId": "L52NYV4KE5BEPILU4L2ERGAVYU",
            "nettoBeløp": 7155,
            "stønadsdager": 20,
            "fom": "2018-01-01",
            "tom": "2018-01-31",
            "utbetalingslinjer": []
          }
        }
        """.let { jackson.readTree(it) }

        @Language("JSON")
        private val jsonKunArbeidsgiveroppdrag = """
        {
          "event": "utbetaling_utbetalt",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "12345678901",
          "aktørId": "<string>",
          "organisasjonsnummer": "999263550",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "forbrukteSykedager": 70,
          "gjenståendeSykedager": 178,
          "stønadsdager": 70,
          "automatiskBehandling": true,
          "type": "UTBETALING",
          "antallVedtak": 1,
          "foreløpigBeregnetSluttPåSykepenger": "2018-12-31",
          "utbetalingsdager": [
            {
              "dato": "2018-01-01",
              "begrunnelser": [
                "SykepengedagerOppbrukt"
              ],
              "type": "AvvistDag"
            }
          ],
          "arbeidsgiverOppdrag": {
            "mottaker": "999263550",
            "fagområde": "SPREF",
            "fagsystemId": "XI2MMEZAJZBVJL2E4K7UM4BQBY",
            "nettoBeløp": 7155,
            "stønadsdager": 20,
            "fom": "2018-01-01",
            "tom": "2018-01-31",
            "utbetalingslinjer": [
              {
                "fom": "2018-01-01",
                "tom": "2018-01-31",
                "dagsats": 1431,
                "totalbeløp": 14310,
                "grad": 66.5,
                "stønadsdager": 20
              },
              {
                "fom": "2018-02-01",
                "tom": "2018-02-28",
                "dagsats": 1431,
                "totalbeløp": 14310,
                "grad": 50.0,
                "stønadsdager": 20
              }
            ]
          },
          "personOppdrag": {
            "mottaker": "<string>",
            "fagområde": "SP",
            "fagsystemId": "L52NYV4KE5BEPILU4L2ERGAVYU",
            "nettoBeløp": 7155,
            "stønadsdager": 20,
            "fom": "2018-01-01",
            "tom": "2018-01-31",
            "utbetalingslinjer": []
          }
        }
        """.let { jackson.readTree(it) }

        @Language("JSON")
        private val jsonKunPersonoppdrag = """
        {
          "event": "utbetaling_utbetalt",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "12345678901",
          "aktørId": "<string>",
          "organisasjonsnummer": "999263550",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "forbrukteSykedager": 70,
          "gjenståendeSykedager": 178,
          "stønadsdager": 70,
          "automatiskBehandling": true,
          "type": "UTBETALING",
          "antallVedtak": 1,
          "foreløpigBeregnetSluttPåSykepenger": "2018-12-31",
          "utbetalingsdager": [
            {
              "dato": "2018-01-01",
              "begrunnelser": [
                "SykepengedagerOppbrukt"
              ],
              "type": "AvvistDag"
            }
          ],
          "arbeidsgiverOppdrag": null,
          "personOppdrag": {
            "mottaker": "<string>",
            "fagområde": "SP",
            "fagsystemId": "L52NYV4KE5BEPILU4L2ERGAVYU",
            "nettoBeløp": 7155,
            "stønadsdager": 20,
            "fom": "2018-01-01",
            "tom": "2018-01-31",
            "utbetalingslinjer": [
              {
                "fom": "2018-02-01",
                "tom": "2018-02-28",
                "dagsats": 1431,
                "totalbeløp": 14310,
                "grad": 50.0,
                "stønadsdager": 20
              }
            ]
          }
        }
        """.let { jackson.readTree(it) }

        private fun nå() = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
}
