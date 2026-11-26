package no.nav.helse.spokelse.tbdutbetaling

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.grunnlag.FpVedtak
import no.nav.helse.spokelse.grunnlag.Utbetalingsperiode
import no.nav.helse.spokelse.personoversikt.Mottaker
import no.nav.helse.spokelse.personoversikt.SpeilV4Linje
import no.nav.helse.spokelse.personoversikt.SpeilV5Linje
import no.nav.helse.spokelse.personoversikt.UtbetaltLinjeMedMottaker
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.erUtbetaling
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.event
import no.nav.helse.spokelse.tbdutbetaling.Melding.Companion.fødselsnummer
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje.Companion.utbetalingslinje
import no.nav.helse.spokelse.tbdvedtak.TbdVedtak
import no.nav.helse.spokelse.tbdvedtak.TbdVedtak.Companion.skjæringstidspunkt
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.*

internal data class Oppdrag(
    internal val fagsystemId: String,
    internal val utbetalingslinjer: List<Utbetalingslinje>,
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
    internal val sistUtbetalt: LocalDateTime,
) {
    init {
        require(arbeidsgiverOppdrag != null || personOppdrag != null) {
            "Hverken arbeidsgiverOppdrag eller personOppdrag er satt."
        }
    }

    val fom = listOfNotNull(arbeidsgiverOppdrag, personOppdrag).flatMap { it.utbetalingslinjer }.minOfOrNull { it.fom }

    private fun Oppdrag?.somFpVedtak() =
        this?.let { oppdrag ->
            FpVedtak(
                vedtaksreferanse = oppdrag.fagsystemId,
                utbetalinger = oppdrag.utbetalingslinjer.map { Utbetalingsperiode(it.fom, it.tom, it.grad) },
                vedtattTidspunkt = sistUtbetalt,
            )
        }

    private fun somFpVedtak() = listOfNotNull(arbeidsgiverOppdrag.somFpVedtak(), personOppdrag.somFpVedtak())

    private fun somSpøkelsePeriode(): List<SpøkelsePeriode> {
        val personidentifikator = Personidentifikator(fødselsnummer)
        val utbetalingslinjer = (arbeidsgiverOppdrag?.utbetalingslinjer ?: emptyList()) + (personOppdrag?.utbetalingslinjer ?: emptyList())
        return utbetalingslinjer.map { utbetalingslinje ->
            SpøkelsePeriode(personidentifikator, utbetalingslinje.fom, utbetalingslinje.tom, utbetalingslinje.grad.toInt(), organisasjonsnummer, setOf("Spleis", "TbdUtbetaling"))
        }
    }

    // En linje med et vedtak som dekker linjens fom, blir SPEIL_V5. Ellers blir den SPEIL_V4.
    private fun somUtbetalteLinjer(vedtak: List<TbdVedtak>): List<UtbetaltLinjeMedMottaker> {
        val personidentifikator = Personidentifikator(fødselsnummer)

        fun Oppdrag?.linjer(mottaker: Mottaker) =
            this?.utbetalingslinjer.orEmpty().map {
                when (val skjæringstidspunkt = vedtak.skjæringstidspunkt(organisasjonsnummer, it.fom)) {
                    null -> SpeilV4Linje(personidentifikator, organisasjonsnummer, mottaker, it.fom, it.tom, it.grad.toInt())
                    else -> SpeilV5Linje(personidentifikator, organisasjonsnummer, mottaker, it.fom, it.tom, it.grad.toInt(), skjæringstidspunkt)
                }
            }
        return arbeidsgiverOppdrag.linjer(Mottaker.ARBEIDSGIVER) + personOppdrag.linjer(Mottaker.PERSON)
    }

    internal companion object {
        private fun JsonNode.oppdrag(path: String) =
            path(path).takeUnless { it.isMissingOrNull() || it.path("utbetalingslinjer").isEmpty }?.let {
                Oppdrag(
                    fagsystemId = it.path("fagsystemId").asString(),
                    utbetalingslinjer = it.path("utbetalingslinjer").values().map { linje -> linje.utbetalingslinje() },
                )
            }

        internal fun JsonNode.utbetaling(sistUtbetalt: LocalDateTime): Utbetaling {
            require(erUtbetaling) { "Kan ikke mappe event $event til utbetaling" }
            val arbeidsgiverOppdrag = oppdrag("arbeidsgiverOppdrag")
            val organisasjonsnummer = path("organisasjonsnummer").asString().takeIf { it.matches("\\d{9}".toRegex()) }
            val personOppdrag = oppdrag("personOppdrag")
            val korrelasjonsId = UUID.fromString(get("korrelasjonsId").asString())
            val gjenståendeSykedager = get("gjenståendeSykedager").asInt()
            return Utbetaling(
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                korrelasjonsId = korrelasjonsId,
                gjenståendeSykedager = gjenståendeSykedager,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                sistUtbetalt = sistUtbetalt,
            )
        }

        internal fun List<Utbetaling>.somFpVedtak() = flatMap(Utbetaling::somFpVedtak)

        internal fun List<Utbetaling>.somSpøkelsePerioder() = flatMap(Utbetaling::somSpøkelsePeriode)

        internal fun List<Utbetaling>.somUtbetalteLinjer(vedtak: List<TbdVedtak>) = flatMap { it.somUtbetalteLinjer(vedtak) }
    }
}
