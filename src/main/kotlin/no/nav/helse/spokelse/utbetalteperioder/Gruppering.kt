package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.request.*
import no.nav.helse.spokelse.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.spokelse.utbetalteperioder.Grupperingsnøkkel.Companion.grupperingsnøkkel

internal enum class GroupBy {
    organisasjonsnummer,
    personidentifikator,
    grad,
    kilde;

    internal companion object {
        val default = setOf(organisasjonsnummer, personidentifikator, grad, kilde)
        fun ApplicationRequest.groupBy(): Set<GroupBy> {
            val groupBy = queryParameters.getAll("groupBy")?.takeUnless { it.isEmpty() } ?: return default
            return groupBy.map { GroupBy.valueOf(it) }.toSet()
        }
    }
}


private data class Grupperingsnøkkel(
    val personidentifikator: Personidentifikator?,
    val organisasjonsnummer: String?,
    val grad: Int?
) {
    companion object {
        fun SpøkelsePeriode.grupperingsnøkkel(groupBy: Set<GroupBy>) = Grupperingsnøkkel(
            personidentifikator = this.personidentifikator.takeIf { GroupBy.personidentifikator in groupBy },
            organisasjonsnummer = this.organisasjonsnummer.takeIf { GroupBy.organisasjonsnummer in groupBy },
            grad = this.grad.takeIf { GroupBy.grad in groupBy }
        )
    }
}

internal class Gruppering(
    private val groupBy: Set<GroupBy>,
    private val infotrygd: Iterable<SpøkelsePeriode>,
    private val spleis: Iterable<SpøkelsePeriode>
) {

    private fun Iterable<SpøkelsePeriode>.gruppér(): List<SpøkelsePeriode> {
        val (medOrganisasjonsnummer, utenOrganisasjonsnummer) = partition { it.organisasjonsnummer != null }

        return medOrganisasjonsnummer.groupBy { it.grupperingsnøkkel(groupBy)}
            .mapValues { (_, gruppertePerioder) ->
                val første = gruppertePerioder.first()
                val sammenhengendePerioder = gruppertePerioder.map { it.periode }.grupperSammenhengendePerioder()
                val tags = gruppertePerioder.associate { it.periode to it.tags }

                sammenhengendePerioder.map { sammenhengendePeriode ->
                    SpøkelsePeriode(
                        personidentifikator = første.personidentifikator,
                        fom = sammenhengendePeriode.start,
                        tom = sammenhengendePeriode.endInclusive,
                        grad = første.grad,
                        organisasjonsnummer = første.organisasjonsnummer,
                        tags = tags.filter { it.key.overlapperMed(sammenhengendePeriode) }.values.flatten().toSet()
                    )
                }
            }.values.flatten() + utenOrganisasjonsnummer
    }

    private fun List<SpøkelsePeriode>.json(): String {
        val utbetaltePerioder = map { objectMapper.createObjectNode().apply {
            // personidentifikator, organisasjonsnummer & grad legges kun til om det er gruppert på dem
            if (GroupBy.personidentifikator in groupBy) put("personidentifikator", "${it.personidentifikator}")
            if (GroupBy.organisasjonsnummer in groupBy) put("organisasjonsnummer", it.organisasjonsnummer)
            if (GroupBy.grad in groupBy) put("grad", it.grad)
            put("fom", "${it.fom}")
            put("tom", "${it.tom}")
            .apply { putArray("tags").let { tags -> it.tags.forEach(tags::add) } }
        }}
        return objectMapper.createObjectNode().apply {
            putArray("utbetaltePerioder").addAll(utbetaltePerioder)
        }.toString()
    }

    internal fun gruppér(): String {
        if (GroupBy.kilde in groupBy) return (infotrygd.gruppér() + spleis.gruppér()).json() // Om vi grupperer på kilde gjøres gruppér på hver enkelt kilde
        return (infotrygd + spleis).gruppér().json() // Om ikke skal gruppere på kilde slår vi de samme før de grupperes
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
