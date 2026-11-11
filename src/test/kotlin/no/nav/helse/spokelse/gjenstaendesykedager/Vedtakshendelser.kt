package no.nav.helse.spokelse.gjenstaendesykedager

import no.nav.helse.spokelse.tbdutbetaling.TbdUtbetalingHåndterer
import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal const val FØDSELSNUMMER = "12345678911"

private val objectMapper = jacksonObjectMapper()

internal fun TbdUtbetalingHåndterer.lagreVedtak(
    korrelasjonsId: UUID = UUID.randomUUID(),
    fødselsnummer: String = FØDSELSNUMMER,
    tom: LocalDate = LocalDate.parse("2023-01-31"),
    gjenståendeSykedager: Int = 221,
    sendt: LocalDateTime = LocalDateTime.parse("2023-02-01T12:00:00"),
    type: String = "utbetaling_utbetalt",
): UUID {
    val fom = tom.withDayOfMonth(1)
    @Language("JSON")
    val json =
        """
        {
          "event": "$type",
          "utbetalingId": "${UUID.randomUUID()}",
          "korrelasjonsId": "$korrelasjonsId",
          "fødselsnummer": "$fødselsnummer",
          "organisasjonsnummer": "999263550",
          "fom": "$fom",
          "tom": "$tom",
          "gjenståendeSykedager": $gjenståendeSykedager,
          "arbeidsgiverOppdrag": {
            "fagsystemId": "${UUID.randomUUID()}",
            "utbetalingslinjer": [{ "fom": "$fom", "tom": "$tom", "grad": 100.0 }]
          },
          "personOppdrag": { "fagsystemId": "${UUID.randomUUID()}", "utbetalingslinjer": [] }
        }
        """
    håndter(objectMapper.readTree(json), sendt)
    return korrelasjonsId
}

internal fun TbdUtbetalingHåndterer.annuller(
    korrelasjonsId: UUID,
    sendt: LocalDateTime,
) {
    @Language("JSON")
    val json =
        """
        {
          "event": "utbetaling_annullert",
          "utbetalingId": "${UUID.randomUUID()}",
          "korrelasjonsId": "$korrelasjonsId",
          "fødselsnummer": "$FØDSELSNUMMER",
          "organisasjonsnummer": "999263550",
          "fom": "2023-01-01",
          "tom": "2023-01-31",
          "arbeidsgiverFagsystemId": "${UUID.randomUUID()}",
          "personFagsystemId": null
        }
        """
    håndter(objectMapper.readTree(json), sendt)
}
