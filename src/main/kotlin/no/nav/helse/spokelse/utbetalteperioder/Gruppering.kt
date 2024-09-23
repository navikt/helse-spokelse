package no.nav.helse.spokelse.utbetalteperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spokelse.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.spokelse.utbetalteperioder.Grupperingsnøkkel.Companion.grupperingsnøkkel
import org.slf4j.LoggerFactory

internal enum class GroupBy {
    organisasjonsnummer,
    personidentifikator,
    grad,
    kilde;

    internal companion object {
        internal val JsonNode.groupBy get(): Set<GroupBy> {
            val oppløsning = path("oppløsning").takeIf { it.isArray }
                ?: throw IllegalStateException("oppløsning må settes i requesten. Men kan settes til en tom liste om man kun ønsker utbetalte perioder per person")
            return oppløsning.map { GroupBy.valueOf(it.asText()) }.toSet()
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

internal sealed interface TagsFilter {
    fun filter(tag: String): Boolean
}

internal data object AlleTags: TagsFilter {
    override fun filter(tag: String) = true
}

internal data object KunEksterneTags: TagsFilter {
    override fun filter(tag: String) = tag == "UsikkerGrad"
}

internal class Gruppering(
    private val groupBy: Set<GroupBy>,
    private val infotrygd: Iterable<SpøkelsePeriode>,
    private val spleis: Iterable<SpøkelsePeriode>,
    private val tagsFilter: TagsFilter = AlleTags
) {

    private fun Iterable<SpøkelsePeriode>.gruppér(): List<SpøkelsePeriode> {
        val (medOrganisasjonsnummer, utenOrganisasjonsnummer) = partition { it.organisasjonsnummer != null }

        return medOrganisasjonsnummer.groupBy { it.grupperingsnøkkel(groupBy) }
            .mapValues { (_, gruppertePerioder) ->
                val første = gruppertePerioder.first()
                val sammenhengendePerioder = gruppertePerioder.map(SpøkelsePeriode::periode).grupperSammenhengendePerioder()
                val tags = gruppertePerioder.groupBy(SpøkelsePeriode::periode).mapValues { (_, values ) -> values.map(SpøkelsePeriode::tags).flatten() }

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
            //  - når vi ikke grupperer på verdiene tar vi bare verdiene fra `first()` - så det er ikke gitt at det er rett for alle periodene
            //  - derfor kan vi heller ikke legge til disse verdiene i responsen
            if (GroupBy.personidentifikator in groupBy) put("personidentifikator", "${it.personidentifikator}")
            if (GroupBy.organisasjonsnummer in groupBy) put("organisasjonsnummer", it.organisasjonsnummer)
            if (GroupBy.grad in groupBy) put("grad", it.grad)
            put("fom", "${it.fom}")
            put("tom", "${it.tom}")
            .apply { putArray("tags").let { tags -> it.tags.filter(tagsFilter::filter).forEach(tags::add) } }
        }}
        return objectMapper.createObjectNode().apply {
            putArray("utbetaltePerioder").addAll(utbetaltePerioder)
        }.toString()
    }

    internal fun gruppér(): String {
        loggRådataFørGruppering()
        if (GroupBy.kilde in groupBy) return (infotrygd.gruppér() + spleis.gruppér()).json() // Om vi grupperer på kilde gjøres gruppér på hver enkelt kilde
        return (infotrygd + spleis).gruppér().json() // Om ikke skal gruppere på kilde slår vi de samme før de grupperes
    }

    private fun loggRådataFørGruppering() {
        val rådata = (infotrygd + spleis).json()
        sikkerlogg.info("/utbetalte-perioder:\nRådata:\n\t$rådata")
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}
