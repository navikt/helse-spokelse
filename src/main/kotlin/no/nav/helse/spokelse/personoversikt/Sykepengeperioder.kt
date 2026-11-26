package no.nav.helse.spokelse.personoversikt

import no.nav.helse.spokelse.felles.Personidentifikator
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Instant
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
    SPEIL_V5(Kilde.SPEIL, 5),
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

// dagsats er beløpet som ble utbetalt per dag, etter gradering. beløp er det som ble utbetalt for hele linjen.
internal sealed interface UtbetaltLinjeMedBeløp : UtbetaltLinje {
    val dagsats: Int
    val beløp: Int
}

// gjenståendeSykedager er tallet fra utbetalingen linjen hører til. utbetaltTidspunkt er når den utbetalingen ble gjort.
internal sealed interface UtbetaltLinjeMedGjenståendeSykedager : UtbetaltLinje {
    val gjenståendeSykedager: Int
    val utbetaltTidspunkt: Instant
}

internal sealed interface UtbetaltLinjeMedSkjæringstidspunkt :
    UtbetaltLinjeMedMottaker,
    UtbetaltLinjeMedBeløp {
    val skjæringstidspunkt: LocalDate
}

internal data class SpeilV1Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    override val gjenståendeSykedager: Int,
    override val utbetaltTidspunkt: Instant,
) : UtbetaltLinjeMedMottaker,
    UtbetaltLinjeMedGjenståendeSykedager {
    override val versjon get() = Versjon.SPEIL_V1
}

internal data class SpeilV2Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    override val dagsats: Int,
    override val beløp: Int,
) : UtbetaltLinjeMedBeløp {
    override val versjon get() = Versjon.SPEIL_V2
}

internal data class SpeilV3Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    override val dagsats: Int,
    override val beløp: Int,
    override val gjenståendeSykedager: Int,
    override val utbetaltTidspunkt: Instant,
) : UtbetaltLinjeMedMottaker,
    UtbetaltLinjeMedBeløp,
    UtbetaltLinjeMedGjenståendeSykedager {
    override val versjon get() = Versjon.SPEIL_V3
}

internal data class SpeilV4Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    override val dagsats: Int,
    override val beløp: Int,
    override val gjenståendeSykedager: Int,
    override val utbetaltTidspunkt: Instant,
) : UtbetaltLinjeMedMottaker,
    UtbetaltLinjeMedBeløp,
    UtbetaltLinjeMedGjenståendeSykedager {
    override val versjon get() = Versjon.SPEIL_V4
}

internal data class SpeilV5Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    override val dagsats: Int,
    override val beløp: Int,
    override val gjenståendeSykedager: Int,
    override val utbetaltTidspunkt: Instant,
    override val skjæringstidspunkt: LocalDate,
) : UtbetaltLinjeMedSkjæringstidspunkt,
    UtbetaltLinjeMedGjenståendeSykedager {
    override val versjon get() = Versjon.SPEIL_V5
}

internal data class InfotrygdV1Linje(
    override val personidentifikator: Personidentifikator,
    override val organisasjonsnummer: String?,
    override val mottaker: Mottaker,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val grad: Int,
    override val dagsats: Int,
    override val beløp: Int,
    val usikkerGrad: Boolean,
    override val skjæringstidspunkt: LocalDate,
    // null når Infotrygd ikke har maksdato.
    val gjenståendeSykedager: Int?,
) : UtbetaltLinjeMedSkjæringstidspunkt {
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

internal val Mottakerutbetalinger<MedBeløp>.beløp get() = utbetalingsperioder.beløp
internal val UtbetalingerUtenKjentMottaker<MedBeløp>.beløp get() = utbetalingsperioder.beløp
private val List<MedBeløp>.beløp get() = sumOf { it.beløp }

internal sealed interface MedBeløp {
    val beløp: Int
}

internal data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
)

internal data class UtbetalingsperiodeMedBeløp(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val dagsats: Int,
    override val beløp: Int,
) : MedBeløp

internal data class InfotrygdUtbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val dagsats: Int,
    override val beløp: Int,
    val usikkerGrad: Boolean,
) : MedBeløp

internal data class SpeilV1Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val gjenståendeSykedager: Int,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<Utbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V1
}

internal data class SpeilV2Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val yrkesaktiviteter: List<Yrkesaktivitet<UtbetalingerUtenKjentMottaker<UtbetalingsperiodeMedBeløp>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V2
}

internal data class SpeilV3Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val gjenståendeSykedager: Int,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<UtbetalingsperiodeMedBeløp>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V3
}

internal data class SpeilV4Periode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val gjenståendeSykedager: Int,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<UtbetalingsperiodeMedBeløp>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V4
}

internal data class SpeilV5Periode(
    val skjæringstidspunkt: LocalDate,
    override val fom: LocalDate,
    override val tom: LocalDate,
    val gjenståendeSykedager: Int,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<UtbetalingsperiodeMedBeløp>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.SPEIL_V5
}

internal data class InfotrygdV1Periode(
    val skjæringstidspunkt: LocalDate,
    override val fom: LocalDate,
    override val tom: LocalDate,
    val gjenståendeSykedager: Int?,
    override val yrkesaktiviteter: List<Yrkesaktivitet<Mottakerutbetalinger<InfotrygdUtbetalingsperiode>>>,
) : Sykepengeperiode {
    override val versjon get() = Versjon.INFOTRYGD_V1
}

internal fun List<UtbetaltLinje>.somSykepengeperioder(): List<Sykepengeperiode> {
    val linjer = utenDuplikater()
    return Versjon.entries
        .flatMap { versjon ->
            when (versjon) {
                Versjon.SPEIL_V1 ->
                    linjer.filterIsInstance<SpeilV1Linje>().somPerioder({ linjerIPerioden, yrkesaktiviteter -> SpeilV1Periode(linjerIPerioden.fom, linjerIPerioden.tom, linjerIPerioden.gjenståendeSykedager, yrkesaktiviteter) }) {
                        it.perMottaker(::somUtbetalingsperioder)
                    }
                Versjon.SPEIL_V2 ->
                    linjer.filterIsInstance<SpeilV2Linje>().somPerioder({ linjerIPerioden, yrkesaktiviteter -> SpeilV2Periode(linjerIPerioden.fom, linjerIPerioden.tom, yrkesaktiviteter) }) {
                        listOf(UtbetalingerUtenKjentMottaker(somUtbetalingsperioderMedBeløp(it)))
                    }
                Versjon.SPEIL_V3 ->
                    linjer.filterIsInstance<SpeilV3Linje>().somPerioder({ linjerIPerioden, yrkesaktiviteter -> SpeilV3Periode(linjerIPerioden.fom, linjerIPerioden.tom, linjerIPerioden.gjenståendeSykedager, yrkesaktiviteter) }) {
                        it.perMottaker(::somUtbetalingsperioderMedBeløp)
                    }
                Versjon.SPEIL_V4 ->
                    linjer.filterIsInstance<SpeilV4Linje>().somPerioder({ linjerIPerioden, yrkesaktiviteter -> SpeilV4Periode(linjerIPerioden.fom, linjerIPerioden.tom, linjerIPerioden.gjenståendeSykedager, yrkesaktiviteter) }) {
                        it.perMottaker(::somUtbetalingsperioderMedBeløp)
                    }
                Versjon.SPEIL_V5 ->
                    linjer.filterIsInstance<SpeilV5Linje>().somPerioderPerTilfelle({ skjæringstidspunkt, linjerIPerioden, yrkesaktiviteter ->
                        SpeilV5Periode(skjæringstidspunkt, linjerIPerioden.fom, linjerIPerioden.tom, linjerIPerioden.gjenståendeSykedager, yrkesaktiviteter)
                    }) { it.perMottaker(::somUtbetalingsperioderMedBeløp) }
                Versjon.INFOTRYGD_V1 ->
                    linjer.filterIsInstance<InfotrygdV1Linje>().somPerioderPerTilfelle({ skjæringstidspunkt, linjerIPerioden, yrkesaktiviteter ->
                        InfotrygdV1Periode(skjæringstidspunkt, linjerIPerioden.fom, linjerIPerioden.tom, linjerIPerioden.gjenståendeSykedager, yrkesaktiviteter)
                    }) { it.perMottaker(::somInfotrygdUtbetalingsperioder) }
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
    periode: (List<L>, List<Yrkesaktivitet<M>>) -> P,
    mottakere: (List<L>) -> List<M>,
): List<P> = sammenhengende(LocalDate::hengerSammenMed).map { linjer -> periode(linjer, linjer.somYrkesaktiviteter(mottakere)) }

// Én periode per sykefraværstilfelle, med én yrkesaktivitet per organisasjonsnummer.
private fun <L : UtbetaltLinjeMedSkjæringstidspunkt, M, P> List<L>.somPerioderPerTilfelle(
    periode: (LocalDate, List<L>, List<Yrkesaktivitet<M>>) -> P,
    mottakere: (List<L>) -> List<M>,
): List<P> =
    groupBy { it.skjæringstidspunkt }
        .toSortedMap()
        .map { (skjæringstidspunkt, linjer) -> periode(skjæringstidspunkt, linjer, linjer.somYrkesaktiviteter(mottakere)) }

private val List<UtbetaltLinje>.fom get() = minOf { it.fom }
private val List<UtbetaltLinje>.tom get() = maxOf { it.tom }

// Gjenstående sykedager ved slutten av perioden er tallet fra linjen som slutter sist. Slutter flere linjer samme dag,
// brukes tallet fra utbetalingen som ble gjort sist, fordi det er den ferskeste utregningen for den dagen.
private val List<UtbetaltLinjeMedGjenståendeSykedager>.gjenståendeSykedager get() = maxWith(compareBy({ it.tom }, { it.utbetaltTidspunkt })).gjenståendeSykedager
private val List<InfotrygdV1Linje>.gjenståendeSykedager get() = maxBy { it.tom }.gjenståendeSykedager

private fun <L : UtbetaltLinje, M> List<L>.somYrkesaktiviteter(mottakere: (List<L>) -> List<M>) =
    groupBy { it.organisasjonsnummer }
        .toSortedMap(nullsLast())
        .map { (organisasjonsnummer, linjerHosArbeidsgiver) -> Yrkesaktivitet(organisasjonsnummer, mottakere(linjerHosArbeidsgiver)) }

private fun <L : UtbetaltLinjeMedMottaker, U> List<L>.perMottaker(utbetalingsperioder: (List<L>) -> List<U>) =
    groupBy { it.mottaker }
        .toSortedMap()
        .map { (mottaker, linjer) -> Mottakerutbetalinger(mottaker, utbetalingsperioder(linjer)) }

private fun somUtbetalingsperioder(linjer: List<UtbetaltLinje>) = linjer.sammenslått({ it.grad }) { fom, tom, grad, _ -> Utbetalingsperiode(fom, tom, grad) }

private fun somUtbetalingsperioderMedBeløp(linjer: List<UtbetaltLinjeMedBeløp>) =
    linjer.sammenslått({ it.grad to it.dagsats }) { fom, tom, (grad, dagsats), sammenslåtte ->
        UtbetalingsperiodeMedBeløp(fom, tom, grad, dagsats, sammenslåtte.sumOf { it.beløp })
    }

private fun somInfotrygdUtbetalingsperioder(linjer: List<InfotrygdV1Linje>) =
    linjer.sammenslått({ Triple(it.grad, it.dagsats, it.usikkerGrad) }) { fom, tom, (grad, dagsats, usikkerGrad), sammenslåtte ->
        InfotrygdUtbetalingsperiode(fom, tom, grad, dagsats, sammenslåtte.sumOf { it.beløp }, usikkerGrad)
    }

// Linjer som er like etter nøkkelen og overlapper eller følger rett etter hverandre, slås sammen.
private fun <L : UtbetaltLinje, K, U> List<L>.sammenslått(
    nøkkel: (L) -> K,
    utbetalingsperiode: (LocalDate, LocalDate, K, List<L>) -> U,
): List<U> =
    groupBy(nøkkel)
        .flatMap { (likeNår, linjer) -> linjer.sammenhengende(LocalDate::følgerRettEtter).map { sammenslåtte -> Triple(sammenslåtte.minOf { it.fom }, likeNår, sammenslåtte) } }
        .sortedBy { (fom, _, _) -> fom }
        .map { (fom, likeNår, sammenslåtte) -> utbetalingsperiode(fom, sammenslåtte.maxOf { it.tom }, likeNår, sammenslåtte) }

// Linjene i rekkefølge etter fom, gruppert så lenge neste linje henger sammen med tom for gruppen så langt.
private fun <L : UtbetaltLinje> List<L>.sammenhengende(hengerSammen: (LocalDate, LocalDate) -> Boolean): List<List<L>> {
    val tidsrom = mutableListOf<MutableList<L>>()
    var tomForTidsrommet: LocalDate? = null
    sortedBy { it.fom }.forEach { linje ->
        val forrigeTom = tomForTidsrommet
        if (forrigeTom == null || !hengerSammen(linje.fom, forrigeTom)) {
            tidsrom.add(mutableListOf(linje))
        } else {
            tidsrom.last().add(linje)
        }
        tomForTidsrommet = maxOf(forrigeTom ?: linje.tom, linje.tom)
    }
    return tidsrom
}

private fun LocalDate.følgerRettEtter(forrigeTom: LocalDate) = this <= forrigeTom.plusDays(1)

// Helg mellom to utbetalinger teller ikke som opphold.
private fun LocalDate.hengerSammenMed(forrigeTom: LocalDate): Boolean {
    if (this <= forrigeTom.plusDays(1)) return true
    return forrigeTom.plusDays(1).datesUntil(this).allMatch { it.dayOfWeek == SATURDAY || it.dayOfWeek == SUNDAY }
}
