package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.spokelse.januar
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.UUID

private val objectMapper = jacksonObjectMapper()

internal fun utbetalingUtbetalt(
    event: String = "utbetaling_utbetalt",
    korrelasjonsId: UUID = UUID.randomUUID(),
    fødselsnummer: String = "12345678911",
    organisasjonsnummer: String = "987654321",
    gjenståendeSykedager: Int = 50,
    arbeidsgiverOppdrag: Oppdrag? = null,
    personOppdrag: Oppdrag? = null,
    fom: LocalDate = 1.januar,
    tom: LocalDate = 31.januar,
): JsonNode =
    objectMapper.readTree(
        """
        {
          "event": "$event",
          "utbetalingId": "${UUID.randomUUID()}",
          "korrelasjonsId": "$korrelasjonsId",
          "fødselsnummer": "$fødselsnummer",
          "organisasjonsnummer": "$organisasjonsnummer",
          "fom": "$fom",
          "tom": "$tom",
          "gjenståendeSykedager": $gjenståendeSykedager,
          "arbeidsgiverOppdrag": ${arbeidsgiverOppdrag.json()},
          "personOppdrag": ${personOppdrag.json()}
        }
        """,
    )

internal fun utbetalingAnnullert(
    korrelasjonsId: UUID = UUID.randomUUID(),
    fødselsnummer: String = "12345678911",
    arbeidsgiverFagsystemId: String? = null,
    personFagsystemId: String? = null,
    fom: LocalDate = 1.januar,
    tom: LocalDate = 31.januar,
): JsonNode =
    objectMapper.readTree(
        """
        {
          "event": "utbetaling_annullert",
          "utbetalingId": "${UUID.randomUUID()}",
          "korrelasjonsId": "$korrelasjonsId",
          "fødselsnummer": "$fødselsnummer",
          "organisasjonsnummer": "987654321",
          "fom": "$fom",
          "tom": "$tom",
          "arbeidsgiverFagsystemId": ${arbeidsgiverFagsystemId?.let { "\"$it\"" }},
          "personFagsystemId": ${personFagsystemId?.let { "\"$it\"" }}
        }
        """,
    )

private fun Oppdrag?.json() =
    this?.let { oppdrag ->
        val linjer = oppdrag.utbetalingslinjer.joinToString { """{ "fom": "${it.fom}", "tom": "${it.tom}", "grad": ${it.grad} }""" }
        """{ "fagsystemId": "${oppdrag.fagsystemId}", "utbetalingslinjer": [$linjer] }"""
    } ?: "null"
