package no.nav.helse.spokelse.tbdutbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.helse.spokelse.grunnlag.FpVedtak
import no.nav.helse.spokelse.grunnlag.Utbetalingsperiode
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.fødselsnummer
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje.Companion.utbetalingslinje
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import java.time.LocalDateTime
import java.util.*

internal data class Oppdrag(
    internal val fagsystemId: String,
    internal val utbetalingslinjer: List<Utbetalingslinje>
) {
    init {
        require(utbetalingslinjer.isNotEmpty()) {
            "Må være minst en utbetalingslinje for å lage ett oppdrag"
        }
    }
}

internal data class Utbetaling(
    internal val fødselsnummer: String,
    internal val organisasjonsnummer: String?,
    internal val korrelasjonsId: UUID,
    internal val gjenståendeSykedager: Int,
    internal val arbeidsgiverOppdrag: Oppdrag?,
    internal val personOppdrag: Oppdrag?,
    internal val sistUtbetalt: LocalDateTime
) {
    init {
        require(arbeidsgiverOppdrag != null || personOppdrag != null) {
            "Hverken arbeidsgiverOppdrag eller personOppdrag er satt."
        }
    }

    private fun Oppdrag?.somFpVedtak() = this?.let { oppdrag -> FpVedtak(
        vedtaksreferanse = oppdrag.fagsystemId,
        utbetalinger = oppdrag.utbetalingslinjer.map { Utbetalingsperiode(it.fom, it.tom, it.grad) },
        vedtattTidspunkt = sistUtbetalt
    )
    }
    private fun somFpVedtak() = listOfNotNull(arbeidsgiverOppdrag.somFpVedtak(), personOppdrag.somFpVedtak())

    private fun somSpøkelsePeriode() : List<SpøkelsePeriode> {
        val personidentifikator = Personidentifikator(fødselsnummer)
        val utbetalingslinjer = (arbeidsgiverOppdrag?.utbetalingslinjer ?: emptyList()) + (personOppdrag?.utbetalingslinjer ?: emptyList())
        return utbetalingslinjer.map { utbetalingslinje ->
            SpøkelsePeriode(personidentifikator, utbetalingslinje.fom, utbetalingslinje.tom, utbetalingslinje.grad.toInt(), organisasjonsnummer, setOf("Spleis", "TbdUtbetaling"))
        }
    }

    internal companion object {
        private fun JsonNode.oppdrag(path:String) = path(path).takeUnless { it.isMissingOrNull() || it.path("utbetalingslinjer").isEmpty }?.let { Oppdrag(
            fagsystemId = it.path("fagsystemId").asText(),
            utbetalingslinjer = it.path("utbetalingslinjer").map { linje -> linje.utbetalingslinje() }
        )}
        internal fun JsonNode.utbetaling(sistUtbetalt: LocalDateTime): Utbetaling {
            require(erUtbetaling) { "Kan ikke mappe event $event til utbetaling" }
            val arbeidsgiverOppdrag = oppdrag("arbeidsgiverOppdrag")
            val organisasjonsnummer = path("organisasjonsnummer").asText().takeIf { it.matches("\\d{9}".toRegex()) }
            val personOppdrag = oppdrag("personOppdrag")
            val korrelasjonsId = UUID.fromString(get("korrelasjonsId").asText())
            val gjenståendeSykedager = get("gjenståendeSykedager").asInt()
            return Utbetaling(
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                korrelasjonsId = korrelasjonsId,
                gjenståendeSykedager = gjenståendeSykedager,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                sistUtbetalt = sistUtbetalt
            )
        }
        internal fun List<Utbetaling>.somFpVedtak() = flatMap(Utbetaling::somFpVedtak)
        internal fun List<Utbetaling>.somSpøkelsePerioder() = flatMap(Utbetaling::somSpøkelsePeriode)
    }
}
