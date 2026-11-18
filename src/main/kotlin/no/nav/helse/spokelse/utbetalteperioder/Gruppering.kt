package no.nav.helse.spokelse.utbetalteperioder

import no.nav.helse.spokelse.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.felles.SpøkelsePeriode
import no.nav.helse.spokelse.utbetalteperioder.Grupperingsnøkkel.Companion.grupperingsnøkkel
import no.nav.sykepenger.libs.logging.loggInfo

internal enum class GroupBy {
    organisasjonsnummer,
    personidentifikator,
    grad,
    kilde,
}

private data class Grupperingsnøkkel(
    val personidentifikator: Personidentifikator?,
    val organisasjonsnummer: String?,
    val grad: Int?,
) {
    companion object {
        fun SpøkelsePeriode.grupperingsnøkkel(groupBy: Set<GroupBy>) =
            Grupperingsnøkkel(
                personidentifikator = this.personidentifikator.takeIf { GroupBy.personidentifikator in groupBy },
                organisasjonsnummer = this.organisasjonsnummer.takeIf { GroupBy.organisasjonsnummer in groupBy },
                grad = this.grad.takeIf { GroupBy.grad in groupBy },
            )
    }
}

internal class Gruppering(
    private val groupBy: Set<GroupBy>,
    private val infotrygd: Iterable<SpøkelsePeriode>,
    private val spleis: Iterable<SpøkelsePeriode>,
) {
    private fun Iterable<SpøkelsePeriode>.gruppér(): List<SpøkelsePeriode> {
        val (medOrganisasjonsnummer, utenOrganisasjonsnummer) = partition { it.organisasjonsnummer != null }

        return medOrganisasjonsnummer
            .groupBy { it.grupperingsnøkkel(groupBy) }
            .mapValues { (_, gruppertePerioder) ->
                val første = gruppertePerioder.first()
                val sammenhengendePerioder = gruppertePerioder.map(SpøkelsePeriode::periode).grupperSammenhengendePerioder()
                val tags = gruppertePerioder.groupBy(SpøkelsePeriode::periode).mapValues { (_, values) -> values.map(SpøkelsePeriode::tags).flatten() }

                sammenhengendePerioder.map { sammenhengendePeriode ->
                    SpøkelsePeriode(
                        personidentifikator = første.personidentifikator,
                        fom = sammenhengendePeriode.start,
                        tom = sammenhengendePeriode.endInclusive,
                        grad = første.grad,
                        organisasjonsnummer = første.organisasjonsnummer,
                        tags =
                            tags
                                .filter { it.key.overlapperMed(sammenhengendePeriode) }
                                .values
                                .flatten()
                                .toSet(),
                    )
                }
            }.values
            .flatten() + utenOrganisasjonsnummer
    }

    // Personidentifikator, organisasjonsnummer og grad er bare riktige for perioden når det er gruppert på dem.
    // Ellers er verdiene hentet fra første periode i gruppen. Svarene må derfor bare vise feltene det er gruppert på.
    internal fun gruppér(): List<SpøkelsePeriode> {
        loggInfo("/utbetalte-perioder: rådata før gruppering", "rådata" to (infotrygd + spleis).toString())
        if (GroupBy.kilde in groupBy) return infotrygd.gruppér() + spleis.gruppér() // Om vi grupperer på kilde gjøres gruppér på hver enkelt kilde
        return (infotrygd + spleis).gruppér() // Om ikke skal gruppere på kilde slår vi de samme før de grupperes
    }
}
