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

// Én utbetalt linje fra en kilde. Hver versjon har sin egen type, med bare feltene kilden garanterer.
internal sealed interface UtbetaltLinje {
    val versjon: Versjon
    val personidentifikator: Personidentifikator
    val organisasjonsnummer: String?
    val fom: LocalDate
    val tom: LocalDate
    val grad: Int
}

internal sealed interface UtbetaltLinjeMedMottaker : UtbetaltLinje {
    val mottaker: Mottaker
}

internal data class SpeilV1Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
) : UtbetaltLinjeMedMottaker {
    override val versjon get() = Versjon.SPEIL_V1
}

internal data class SpeilV2Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
) : UtbetaltLinje {
    override val versjon get() = Versjon.SPEIL_V2
}

internal data class SpeilV3Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
) : UtbetaltLinjeMedMottaker {
    override val versjon get() = Versjon.SPEIL_V3
}

internal data class SpeilV4Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
) : UtbetaltLinjeMedMottaker {
    override val versjon get() = Versjon.SPEIL_V4
}

internal data class InfotrygdV1Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    val usikkerGrad: Boolean,
) : UtbetaltLinjeMedMottaker {
    override val versjon get() = Versjon.INFOTRYGD_V1
}

// En periode per versjon. Hver periode har bare feltene versjonen garanterer.
internal sealed interface Sykepengeperiode {
    val versjon: Versjon
    val fom: LocalDate
    val tom: LocalDate
    val yrkesaktiviteter: List<Yrkesaktivitet<*>>
}

internal data class Yrkesaktivitet<out M>(
    val organisasjonsnummer: String?,
    val mottakere: List<M>,
)

internal data class Mottakerutbetalinger<out U>(
    val mottaker: Mottaker,
    val utbetalingsperioder: List<U>,
)

internal data class UtbetalingerUtenKjentMottaker<out U>(
    val utbetalingsperioder: List<U>,
)

internal data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
)

internal data class InfotrygdUtbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val usikkerGrad: Boolean,
)

internal data class SpeilV1Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<Utbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V1
}

internal data class SpeilV2Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val yrkesaktiviteter: List<Yrkesaktivitet<UtbetalingerUtenKjentMottaker<Utbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V2
}

internal data class SpeilV3Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<Utbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V3
}

internal data class SpeilV4Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<Utbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V4
}

internal data class InfotrygdV1Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<InfotrygdUtbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.INFOTRYGD_V1
}

internal fun List<UtbetaltLinje>.somSykepengeperioder(): List<Sykepengeperiode> {
    val linjer = utenDuplikater()
    return Versjon.entries
        .flatMap { versjon ->
            when (versjon) {
                Versjon.SPEIL_V1 -> linjer.filterIsInstance<SpeilV1Linje>().somPerioder(::SpeilV1Periode) { it.perMottaker(::somUtbetalingsperioder) }
                Versjon.SPEIL_V2 -> linjer.filterIsInstance<SpeilV2Linje>().somPerioder(::SpeilV2Periode) { listOf(UtbetalingerUtenKjentMottaker(somUtbetalingsperioder(it))) }
                Versjon.SPEIL_V3 -> linjer.filterIsInstance<SpeilV3Linje>().somPerioder(::SpeilV3Periode) { it.perMottaker(::somUtbetalingsperioder) }
                Versjon.SPEIL_V4 -> linjer.filterIsInstance<SpeilV4Linje>().somPerioder(::SpeilV4Periode) { it.perMottaker(::somUtbetalingsperioder) }
                Versjon.INFOTRYGD_V1 -> linjer.filterIsInstance<InfotrygdV1Linje>().somPerioder(::InfotrygdV1Periode) { it.perMottaker(::somInfotrygdUtbetalingsperioder) }
            }
        }.sortedWith(compareBy({ it.fom }, { it.versjon }))
}

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

// Én periode per tidsrom med sammenhengende utbetaling, med én yrkesaktivitet per organisasjonsnummer.
private fun <L : UtbetaltLinje, M, P> List<L>.somPerioder(
    periode: (LocalDate, LocalDate, List<Yrkesaktivitet<M>>) -> P,
    mottakere: (List<L>) -> List<M>,
): List<P> =
    sammenhengende().map { linjer ->
        periode(
            linjer.minOf { it.fom },
            linjer.maxOf { it.tom },
            linjer
                .groupBy { it.organisasjonsnummer }
                .toSortedMap(nullsLast())
                .map { (organisasjonsnummer, linjerHosArbeidsgiver) -> Yrkesaktivitet(organisasjonsnummer, mottakere(linjerHosArbeidsgiver)) },
        )
    }

private fun <L : UtbetaltLinjeMedMottaker, U> List<L>.perMottaker(utbetalingsperioder: (List<L>) -> List<U>) =
    groupBy { it.mottaker }
        .toSortedMap()
        .map { (mottaker, linjer) -> Mottakerutbetalinger(mottaker, utbetalingsperioder(linjer)) }

private fun somUtbetalingsperioder(linjer: List<UtbetaltLinje>) = linjer.sammenslått({ it.grad }) { periode, grad -> Utbetalingsperiode(periode.start, periode.endInclusive, grad) }

private fun somInfotrygdUtbetalingsperioder(linjer: List<InfotrygdV1Linje>) =
    linjer.sammenslått({ it.grad to it.usikkerGrad }) { periode, (grad, usikkerGrad) ->
        InfotrygdUtbetalingsperiode(periode.start, periode.endInclusive, grad, usikkerGrad)
    }

// Linjer som henger sammen og er like etter nøkkelen, slås sammen.
private fun <L : UtbetaltLinje, K, U> List<L>.sammenslått(
    nøkkel: (L) -> K,
    utbetalingsperiode: (Periode, K) -> U,
): List<U> =
    groupBy(nøkkel)
        .flatMap { (likeNår, linjer) -> linjer.map { Periode(it.fom, it.tom) }.grupperSammenhengendePerioder().map { it to likeNår } }
        .sortedBy { (periode, _) -> periode.start }
        .map { (periode, likeNår) -> utbetalingsperiode(periode, likeNår) }

private fun <L : UtbetaltLinje> List<L>.sammenhengende(): List<List<L>> {
    val tidsrom = mutableListOf<MutableList<L>>()
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
