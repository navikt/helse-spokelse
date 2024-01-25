package no.nav.helse.spokelse

import java.time.LocalDate

internal class Periode(fom: LocalDate, tom: LocalDate): ClosedRange<LocalDate>, Iterable<LocalDate> {

    override val start: LocalDate = fom
    override val endInclusive: LocalDate = tom

    init {
        require(start <= endInclusive) { "fom ($start) kan ikke være etter tom ($endInclusive)" }
    }

    internal fun overlapperMed(other: Periode) = overlappendePeriode(other) != null

    private fun overlappendePeriode(other: Periode): Periode? {
        val start = maxOf(this.start, other.start)
        val slutt = minOf(this.endInclusive, other.endInclusive)
        if (start > slutt) return null
        return Periode(start, slutt)
    }

    private fun merge(other: Periode): Periode {
        if (this.overlapperMed(other) || this.endInclusive.plusDays(1) == other.start || other.endInclusive.plusDays(1) == this.start) {
            return this + other
        }
        return this
    }

    override operator fun iterator() = object : Iterator<LocalDate> {
        private var currentDate: LocalDate = start

        override fun hasNext() = endInclusive >= currentDate

        override fun next() =
            currentDate.also { currentDate = it.plusDays(1) }
    }

    operator fun contains(other: Periode) =
        this.start <= other.start && this.endInclusive >= other.endInclusive

    operator fun plus(annen: Periode?): Periode {
        if (annen == null) return this
        return Periode(minOf(this.start, annen.start), maxOf(this.endInclusive, annen.endInclusive))
    }

    override fun equals(other: Any?) = other is Periode && this.start == other.start && this.endInclusive == other.endInclusive
    override fun hashCode() = start.hashCode() * 37 + endInclusive.hashCode()

    internal companion object {
        internal fun Iterable<Periode>.grupperSammenhengendePerioder(): List<Periode> {
            val resultat = mutableListOf<Periode>()
            val sortert = sortedBy { it.start }
            sortert.forEachIndexed { index, periode ->
                if (resultat.any { champion -> periode in champion }) return@forEachIndexed // en annen periode har spist opp denne
                resultat.add(sortert.subList(index, sortert.size).reduce { champion, challenger ->
                    champion.merge(challenger)
                })
            }
            return resultat
        }
    }
}
