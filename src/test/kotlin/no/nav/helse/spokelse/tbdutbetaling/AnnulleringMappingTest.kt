package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spokelse.tbdutbetaling.Annullering.Companion.annullering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AnnulleringMappingTest {

    @Test
    fun `annullering begge oppdrag`() {
        assertEquals(Annullering("XI2MMEZAJZBVJL2E4K7UM4BQBY", "L52NYV4KE5BEPILU4L2ERGAVYU"), jsonAnnulleringBeggeOppdrag.annullering())
    }

    @Test
    fun `annullering arbeidsgiveroppdrag`() {
        assertEquals(Annullering("XI2MMEZAJZBVJL2E4K7UM4BQBY", null), jsonAnnulleringArbeidsgiveroppdrag.annullering())
    }

    @Test
    fun `annullering personoppdrag`() {
        assertEquals(Annullering(null, "L52NYV4KE5BEPILU4L2ERGAVYU"), jsonAnnulleringPersonoppdrag.annullering())
    }

    private companion object {
        private val jackson = jacksonObjectMapper()

        @Language("JSON")
        val jsonAnnulleringBeggeOppdrag = """
        {
          "event": "utbetaling_annullert",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "<string>",
          "organisasjonsnummer": "999263550",
          "orgnummer": "999263550",
          "tidsstempel": "2022-06-10T19:06:26.765",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "arbeidsgiverFagsystemId": "XI2MMEZAJZBVJL2E4K7UM4BQBY",
          "personFagsystemId": "L52NYV4KE5BEPILU4L2ERGAVYU"
        }
        """.let { jackson.readTree(it) }

        @Language("JSON")
        val jsonAnnulleringArbeidsgiveroppdrag = """
        {
          "event": "utbetaling_annullert",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "<string>",
          "organisasjonsnummer": "999263550",
          "orgnummer": "999263550",
          "tidsstempel": "2022-06-10T19:06:26.765",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "arbeidsgiverFagsystemId": "XI2MMEZAJZBVJL2E4K7UM4BQBY",
          "personFagsystemId": null
        }
        """.let { jackson.readTree(it) }

        @Language("JSON")
        val jsonAnnulleringPersonoppdrag = """
        {
          "event": "utbetaling_annullert",
          "utbetalingId": "446eca54-befd-4851-acc3-ec300a20932a",
          "korrelasjonsId": "a43696c7-e824-4140-b8a7-348efe7128cc",
          "fødselsnummer": "<string>",
          "organisasjonsnummer": "999263550",
          "orgnummer": "999263550",
          "tidsstempel": "2022-06-10T19:06:26.765",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "arbeidsgiverFagsystemId": null,
          "personFagsystemId": "L52NYV4KE5BEPILU4L2ERGAVYU"
        }
        """.let { jackson.readTree(it) }
    }
}
