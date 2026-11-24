package no.nav.helse.spokelse.personoversikt

import no.nav.helse.spokelse.Periode
import no.nav.helse.spokelse.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.spokelse.felles.Personidentifikator
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate

internal enum class Kilde { SPEIL, INFOTRYGD }

// Når samme linje finnes i flere kilder i Speil, er bare linjen fra versjonen med høyest rang med.
internal enum class Versjon(
    internal val kilde: Kilde,
    internal val rangVedDuplikat: Int,
) {
    SPEIL_V1(Kilde.SPEIL, 1),
    SPEIL_V2(Kilde.SPEIL, 2),
    SPEIL_V3(Kilde.SPEIL, 3),
    SPEIL_V4(Kilde.SPEIL, 4),
    INFOTRYGD_V1(Kilde.INFOTRYGD, 0),
}

internal enum class Mottaker { ARBEIDSGIVER, PERSON }

internal data class UtbetaltLinje(
    val versjon: Versjon,
    val personidentifikator: Personidentifikator,
    val organisasjonsnummer: String?,
    val mottaker: Mottaker?,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val usikkerGrad: Boolean,
)

internal data class Sykepengeperiode(
    val versjon: Versjon,
    val fom: LocalDate,
    val tom: LocalDate,
    val yrkesaktiviteter: List<Yrkesaktivitet>,
)

internal data class Yrkesaktivitet(
    val organisasjonsnummer: String?,
    val mottakere: List<Mottakerutbetalinger>,
)

internal data class Mottakerutbetalinger(
    val mottaker: Mottaker?,
    val utbetalingsperioder: List<Utbetalingsperiode>,
)

internal data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val usikkerGrad: Boolean,
)

internal fun List<UtbetaltLinje>.somSykepengeperioder(): List<Sykepengeperiode> =
    utenDuplikater()
        .groupBy { it.versjon }
        .flatMap { (versjon, linjer) -> linjer.sammenhengende().map { versjon to it } }
        .map { (versjon, linjer) ->
            Sykepengeperiode(
                versjon = versjon,
                fom = linjer.minOf { it.fom },
                tom = linjer.maxOf { it.tom },
                yrkesaktiviteter = linjer.somYrkesaktiviteter(),
            )
        }.sortedWith(compareBy({ it.fom }, { it.versjon }))

private data class Duplikatnøkkel(
    val personidentifikator: Personidentifikator,
    val organisasjonsnummer: String?,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
)

// Duplikater finnes bare på tvers av versjoner. Innenfor versjonen som vinner, beholdes alle linjene, også til hver mottaker.
private fun List<UtbetaltLinje>.utenDuplikater(): List<UtbetaltLinje> {
    val (speil, infotrygd) = partition { it.versjon.kilde == Kilde.SPEIL }
    return speil
        .groupBy { Duplikatnøkkel(it.personidentifikator, it.organisasjonsnummer, it.fom, it.tom, it.grad) }
        .values
        .flatMap { duplikater ->
            val høyestRangerteVersjon = duplikater.maxOf { it.versjon.rangVedDuplikat }
            duplikater.filter { it.versjon.rangVedDuplikat == høyestRangerteVersjon }
        } + infotrygd
}

private fun List<UtbetaltLinje>.somYrkesaktiviteter() =
    groupBy { it.organisasjonsnummer }
        .toSortedMap(nullsLast())
        .map { (organisasjonsnummer, linjer) -> Yrkesaktivitet(organisasjonsnummer, linjer.somMottakerutbetalinger()) }

private fun List<UtbetaltLinje>.somMottakerutbetalinger() =
    groupBy { it.mottaker }
        .toSortedMap(nullsLast())
        .map { (mottaker, linjer) ->
            Mottakerutbetalinger(mottaker, linjer.somUtbetalingsperioder())
        }

private fun List<UtbetaltLinje>.somUtbetalingsperioder() =
    groupBy { it.grad to it.usikkerGrad }
        .flatMap { (gradOgUsikkerGrad, linjer) ->
            val (grad, usikkerGrad) = gradOgUsikkerGrad
            linjer
                .map { Periode(it.fom, it.tom) }
                .grupperSammenhengendePerioder()
                .map { Utbetalingsperiode(it.start, it.endInclusive, grad, usikkerGrad) }
        }.sortedBy { it.fom }

private fun List<UtbetaltLinje>.sammenhengende(): List<List<UtbetaltLinje>> {
    val tidsrom = mutableListOf<MutableList<UtbetaltLinje>>()
    var tomForTidsrommet: LocalDate? = null
    sortedBy { it.fom }.forEach { linje ->
        val forrigeTom = tomForTidsrommet
        if (forrigeTom == null || !linje.fom.hengerSammenMed(forrigeTom)) {
            tidsrom.add(mutableListOf(linje))
        } else {
            tidsrom.last().add(linje)
        }
        tomForTidsrommet = maxOf(forrigeTom ?: linje.tom, linje.tom)
    }
    return tidsrom
}

// Helg mellom to utbetalinger teller ikke som opphold.
private fun LocalDate.hengerSammenMed(forrigeTom: LocalDate): Boolean {
    if (this <= forrigeTom.plusDays(1)) return true
    return forrigeTom.plusDays(1).datesUntil(this).allMatch { it.dayOfWeek == SATURDAY || it.dayOfWeek == SUNDAY }
}
