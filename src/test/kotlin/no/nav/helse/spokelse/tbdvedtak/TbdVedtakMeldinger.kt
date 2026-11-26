package no.nav.helse.spokelse.tbdvedtak

import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// Et utdrag av vedtak fattet på tbd.vedtak (versjon 1.4.0), med feltene Spøkelse leser og noen av de andre.
internal fun vedtakFattet(
    fødselsnummer: String = "12345678911",
    organisasjonsnummer: String = "987654321",
    fom: LocalDate = LocalDate.parse("2018-01-01"),
    tom: LocalDate = LocalDate.parse("2018-01-31"),
    skjæringstidspunkt: LocalDate = fom,
    vedtakFattetTidspunkt: LocalDateTime = LocalDateTime.parse("2018-02-01T12:00:00.123"),
) = jacksonObjectMapper().readTree(
    @Language("JSON")
    """
    {
      "versjon": "1.4.0",
      "fødselsnummer": "$fødselsnummer",
      "aktørId": "1234567890123",
      "organisasjonsnummer": "$organisasjonsnummer",
      "yrkesaktivitetstype": "${if (organisasjonsnummer == "SELVSTENDIG") "SELVSTENDIG" else "ARBEIDSTAKER"}",
      "fom": "$fom",
      "tom": "$tom",
      "skjæringstidspunkt": "$skjæringstidspunkt",
      "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
      "utbetalingId": "${UUID.randomUUID()}",
      "dokumenter": [],
      "inntekt": 30000.0,
      "sykepengegrunnlag": 360000.0,
      "tags": []
    }
    """,
)

internal fun vedtakAnnullert(
    fødselsnummer: String = "12345678911",
    organisasjonsnummer: String = "987654321",
    fom: LocalDate = LocalDate.parse("2018-01-01"),
    tom: LocalDate = LocalDate.parse("2018-01-31"),
) = jacksonObjectMapper().readTree(
    @Language("JSON")
    """
    {
      "event": "vedtak_annullert",
      "versjon": "1.0.0",
      "fødselsnummer": "$fødselsnummer",
      "aktørId": "1234567890123",
      "organisasjonsnummer": "$organisasjonsnummer",
      "fom": "$fom",
      "tom": "$tom"
    }
    """,
)
