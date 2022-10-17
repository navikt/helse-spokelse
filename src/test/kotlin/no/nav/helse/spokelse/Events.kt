package no.nav.helse.spokelse

import org.apache.commons.codec.binary.Base32
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import kotlin.random.Random

internal object Events {
    internal fun genererFagsystemId() = Base32().encodeToString(Random.Default.nextBytes(32)).take(26)

    @Language("JSON")
    internal fun sendtSøknadNavEvent(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""


    @Language("JSON")
    internal fun sendtSøknadArbeidsgiverEvent(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_arbeidsgiver",
            "@id": "${sykmelding.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

    @Language("JSON")
    internal fun inntektsmeldingEvent(hendelse: Hendelse) =
        """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""

    @Language("JSON")
    internal fun annulleringEvent(
        eventName: String = "utbetaling_annullert",
        fødselsnummer: String,
        orgnummer: String,
        arbeidsgiverFagsystemId: String?,
        personFagsystemId: String? = null,
        fom: LocalDate,
        tom: LocalDate
    ) =
        """{
            "fødselsnummer": "$fødselsnummer",
            "organisasjonsnummer": "$orgnummer",
            "arbeidsgiverFagsystemId": ${if (arbeidsgiverFagsystemId != null) "\"$arbeidsgiverFagsystemId\"" else null},
            "personFagsystemId": ${if (personFagsystemId != null) "\"$personFagsystemId\"" else null},
            "fom": "$fom",
            "tom": "$tom",
            "@event_name": "$eventName"
        }
        """
}
