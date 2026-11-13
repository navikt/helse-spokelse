package no.nav.helse.spokelse

import kotliquery.Session
import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.single
import no.nav.helse.spokelse.felles.update
import javax.sql.DataSource

internal class UtbetaltDao(
    val datasource: DataSource,
) {
    internal fun opprett(vedtak: Vedtak) {
        sessionOf(datasource).use { session ->
            session.transaction {
                it.opprett(vedtak)
            }
        }
    }

    private fun Session.opprett(vedtak: Vedtak) {
        val vedtakId =
            asSQL(
                """
                INSERT INTO vedtak(fodselsnummer, orgnummer, opprettet, fom, tom, forbrukte_sykedager, gjenstående_sykedager, sykmelding_id, soknad_id, inntektsmelding_id, hendelse_id)
                VALUES (:fodselsnummer, :orgnummer, :opprettet, :fom, :tom, :forbrukteSykedager, :gjenstaendeSykedager, :sykmeldingId, :soknadId, :inntektsmeldingId, :hendelseId)
                RETURNING id
                """.trimIndent(),
                "fodselsnummer" to vedtak.fødselsnummer,
                "orgnummer" to vedtak.orgnummer,
                "opprettet" to vedtak.opprettet,
                "fom" to vedtak.fom,
                "tom" to vedtak.tom,
                "forbrukteSykedager" to vedtak.forbrukteSykedager,
                "gjenstaendeSykedager" to vedtak.gjenståendeSykedager,
                "sykmeldingId" to vedtak.dokumenter.sykmelding.dokumentId,
                "soknadId" to vedtak.dokumenter.søknad.dokumentId,
                "inntektsmeldingId" to vedtak.dokumenter.inntektsmelding?.dokumentId,
                "hendelseId" to vedtak.hendelseId,
            ).single(this) { row -> row.long("id") }
        opprettOppdrag(requireNotNull(vedtakId), vedtak.oppdrag)
    }

    private fun Session.opprettOppdrag(
        vedtakId: Long,
        oppdragListe: List<Vedtak.Oppdrag>,
    ) {
        oppdragListe.forEach { oppdrag ->
            val oppdragId =
                asSQL(
                    """
                    INSERT INTO oppdrag(vedtak_id, mottaker, fagområde, fagsystemid, totalbeløp)
                    VALUES (:vedtakId, :mottaker, :fagomrade, :fagsystemId, :totalbelop)
                    RETURNING id
                    """.trimIndent(),
                    "vedtakId" to vedtakId,
                    "mottaker" to oppdrag.mottaker,
                    "fagomrade" to oppdrag.fagområde,
                    "fagsystemId" to oppdrag.fagsystemId,
                    "totalbelop" to oppdrag.totalbeløp,
                ).single(this) { row -> row.long("id") }
            opprettUtbetalingslinjer(requireNotNull(oppdragId), oppdrag.utbetalingslinjer)
        }
    }

    private fun Session.opprettUtbetalingslinjer(
        oppdragId: Long,
        linjeListe: List<Vedtak.Oppdrag.Utbetalingslinje>,
    ) {
        linjeListe.forEach { linje ->
            asSQL(
                """
                INSERT INTO utbetaling(oppdrag_id, fom, tom, dagsats, grad, belop, sykedager)
                VALUES (:oppdragId, :fom, :tom, :dagsats, :grad, :belop, :sykedager)
                """.trimIndent(),
                "oppdragId" to oppdragId,
                "fom" to linje.fom,
                "tom" to linje.tom,
                "dagsats" to linje.dagsats,
                "grad" to linje.grad,
                "belop" to linje.beløp,
                "sykedager" to linje.sykedager,
            ).update(this)
        }
    }
}
