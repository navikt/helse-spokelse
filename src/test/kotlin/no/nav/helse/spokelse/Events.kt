package no.nav.helse.spokelse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.commons.codec.binary.Base32
import org.intellij.lang.annotations.Language
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random
import kotlin.streams.asSequence

internal object Events {
    internal fun genererFagsystemId() = Base32().encodeToString(Random.Default.nextBytes(32)).take(26)

    internal fun LocalDateTime.formater() = format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

    internal fun utbetaltEvent(
        fødselsnummer: String,
        orgnummer: String,
        grad: Double,
        fom: LocalDate,
        tom: LocalDate,
        tidspunkt: LocalDateTime,
        hendelser: List<Hendelse>,
        arbeidsgiverFagsystemId: String?,
        personFagsystemId: String?,
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ): String {
        val utbetalt = mutableListOf<Map<String, Any>>()
        if (arbeidsgiverFagsystemId != null) {
            utbetalt.add(
                oppdrag(
                    mottaker = orgnummer,
                    fagsystemId = arbeidsgiverFagsystemId,
                    fagområde = "SPREF",
                    fom = fom,
                    tom = tom,
                    grad = grad,
                    sats = 1431
                )
            )
        }
        if (personFagsystemId != null) {
            utbetalt.add(
                oppdrag(
                    mottaker = fødselsnummer,
                    fagsystemId = personFagsystemId,
                    fagområde = "SP",
                    fom = fom,
                    tom = tom,
                    grad = grad,
                    sats = 1337
                )
            )
        }
        val hendelse = mapOf(
            "@event_name" to "utbetalt",
            "@opprettet" to tidspunkt.formater(),
            "@id" to "${UUID.randomUUID()}",
            "aktørId" to "aktørId",
            "fødselsnummer" to fødselsnummer,
            "organisasjonsnummer" to orgnummer,
            "utbetalingId" to "$utbetalingId",
            "vedtaksperiodeId" to "$vedtaksperiodeId",
            "hendelser" to hendelser.map { it.hendelseId },
            "utbetalt" to utbetalt,
            "fom" to "$fom",
            "tom" to "$tom",
            "forbrukteSykedager" to sykedager(fom, tom),
            "gjenståendeSykedager" to 248 - sykedager(fom, tom),
        )

        return jacksonObjectMapper().writeValueAsString(hendelse)
    }

    private fun oppdrag(
        mottaker: String,
        fagsystemId: String,
        fagområde: String,
        grad: Double,
        fom: LocalDate,
        tom: LocalDate,
        sats: Int
    ) = mapOf(
        "mottaker" to mottaker,
        "fagområde" to fagområde,
        "fagsystemId" to fagsystemId,
        "totalbeløp" to sats * 5,
        "utbetalingslinjer" to listOf(
            mapOf(
                "fom" to "$fom",
                "tom" to "$tom",
                "sats" to sats,
                "dagsats" to sats,
                "beløp" to sats,
                "grad" to grad,
                "sykedager" to sykedager(fom, tom)
            )
        )
    )

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

    @Language("JSON")
    internal fun sendtSøknadNavEvent(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
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
