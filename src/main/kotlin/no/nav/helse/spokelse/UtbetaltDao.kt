package no.nav.helse.spokelse

import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

data class Dokumenter(
    val sykmelding: Hendelse,
    val søknad: Hendelse,
    val inntektsmelding: Hendelse?
) {
    init {
        require(sykmelding.type == Dokument.Sykmelding)
        require(søknad.type == Dokument.Søknad)
        inntektsmelding?.also { require(it.type == Dokument.Inntektsmelding) }
    }
}

class UtbetaltDao(val datasource: DataSource) {
    fun opprett(vedtak: Vedtak) {
        sessionOf(datasource, true).use { session ->
            session.transaction {
                it.opprett(vedtak)
            }
        }
    }

    private fun Session.opprett(vedtak: Vedtak) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtak(
            fodselsnummer,
            orgnummer,
            opprettet,
            fom,
            tom,
            forbrukte_sykedager,
            gjenstående_sykedager,
            sykmelding_id,
            soknad_id,
            inntektsmelding_id,
            hendelse_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)"""
        val key = run(
            queryOf(
                query,
                vedtak.fødselsnummer,
                vedtak.orgnummer,
                vedtak.opprettet,
                vedtak.fom,
                vedtak.tom,
                vedtak.forbrukteSykedager,
                vedtak.gjenståendeSykedager,
                vedtak.dokumenter.sykmelding.dokumentId,
                vedtak.dokumenter.søknad.dokumentId,
                vedtak.dokumenter.inntektsmelding?.dokumentId,
                vedtak.hendelseId
            ).asUpdateAndReturnGeneratedKey
        )
        opprettOppdrag(requireNotNull(key), vedtak.oppdrag)
    }

    private fun Session.opprettOppdrag(vedtakKey: Long, oppdragListe: List<Vedtak.Oppdrag>) {
        @Language("PostgreSQL")
        val query = """INSERT INTO oppdrag(vedtak_id, mottaker, fagområde, fagsystemid, totalbeløp)
            VALUES(?,?,?,?,?)"""
        oppdragListe.forEach { oppdrag ->
            val key = run(
                queryOf(
                    query,
                    vedtakKey,
                    oppdrag.mottaker,
                    oppdrag.fagområde,
                    oppdrag.fagsystemId,
                    oppdrag.totalbeløp
                ).asUpdateAndReturnGeneratedKey
            )
            opprettUtbetalingslinjer(requireNotNull(key), oppdrag.utbetalingslinjer)
        }
    }

    private fun Session.opprettUtbetalingslinjer(oppdragKey: Long, linjeListe: List<Vedtak.Oppdrag.Utbetalingslinje>) {
        @Language("PostgreSQL")
        val query = """INSERT INTO utbetaling(oppdrag_id, fom, tom, dagsats, grad, belop, sykedager)
            VALUES(?,?,?,?,?,?,?)"""
        linjeListe.forEach { linje ->
            run(
                queryOf(
                    query,
                    oppdragKey,
                    linje.fom,
                    linje.tom,
                    linje.dagsats,
                    linje.grad,
                    linje.beløp,
                    linje.sykedager
                ).asUpdate
            )
        }
    }
}
