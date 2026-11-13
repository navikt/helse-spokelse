package no.nav.helse.spokelse

import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.single
import no.nav.helse.spokelse.felles.update
import javax.sql.DataSource

internal class LagreVedtakDao(
    private val dataSource: DataSource,
) {
    fun save(vedtak: OldVedtak) {
        sessionOf(dataSource).use {
            it.transaction { transaksjon ->
                val vedtakId =
                    asSQL(
                        """
                        INSERT INTO old_vedtak(vedtaksperiode_id, fodselsnummer, orgnummer, opprettet, forbrukte_sykedager, gjenstående_sykedager, sykmelding_id, soknad_id, inntektsmelding_id)
                        VALUES (:vedtaksperiodeId, :fodselsnummer, :orgnummer, :opprettet, :forbrukteSykedager, :gjenstaendeSykedager, :sykmeldingId, :soknadId, :inntektsmeldingId)
                        RETURNING id
                        """.trimIndent(),
                        "vedtaksperiodeId" to vedtak.vedtaksperiodeId,
                        "fodselsnummer" to vedtak.fødselsnummer,
                        "orgnummer" to vedtak.orgnummer,
                        "opprettet" to vedtak.opprettet,
                        "forbrukteSykedager" to vedtak.forbrukteSykedager,
                        "gjenstaendeSykedager" to vedtak.gjenståendeSykedager,
                        "sykmeldingId" to vedtak.dokumenter.sykmelding.dokumentId,
                        "soknadId" to vedtak.dokumenter.søknad.dokumentId,
                        "inntektsmeldingId" to vedtak.dokumenter.inntektsmelding?.dokumentId,
                    ).single(transaksjon) { row -> row.long("id") }

                vedtak.utbetalinger.forEach { utbetaling ->
                    asSQL(
                        """
                        INSERT INTO old_utbetaling(vedtak_id, fom, tom, grad, dagsats, belop, totalbelop)
                        VALUES (:vedtakId, :fom, :tom, :grad, :dagsats, :belop, :totalbelop)
                        """.trimIndent(),
                        "vedtakId" to vedtakId,
                        "fom" to utbetaling.fom,
                        "tom" to utbetaling.tom,
                        "grad" to utbetaling.grad,
                        "dagsats" to utbetaling.dagsats,
                        "belop" to utbetaling.beløp,
                        "totalbelop" to utbetaling.totalbeløp,
                    ).update(transaksjon)
                }
            }
        }
    }
}
