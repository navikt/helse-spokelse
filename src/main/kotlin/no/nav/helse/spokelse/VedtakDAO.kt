package no.nav.helse.spokelse

import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

class VedtakDAO(private val dataSource: DataSource) {
    fun save(vedtak: Vedtak) {
        sessionOf(dataSource, true).use { session ->
            val vedtakId = session.run(
                queryOf(
                    "INSERT INTO vedtak(fodselsnummer, forste_fravarsdag, opprettet) VALUES (?, ?, ?)",
                    vedtak.fødselsnummer,
                    vedtak.førsteFraværsdag,
                    vedtak.opprettet
                ).asUpdateAndReturnGeneratedKey
            )

            vedtak.utbetalinger.forEach {
                session.run(
                    queryOf(
                        "INSERT INTO utbetaling(vedtak_id, fom, tom, grad) VALUES (?, ?, ?, ?)",
                        vedtakId,
                        it.fom,
                        it.tom,
                        it.grad
                    ).asUpdate
                )
            }
        }
    }

    fun hentVedtakListe(fnr: String) = sessionOf(dataSource)
        .use { session ->
            session.run(
                queryOf(
                    "SELECT id, forste_fravarsdag, opprettet FROM vedtak WHERE fodselsnummer = ?",
                    fnr
                ).map { row ->
                    val utbetalinger = session.run(
                        queryOf(
                            "SELECT fom, tom, grad FROM utbetaling WHERE vedtak_id = ?",
                            row.long("id"))
                            .map { utbetalingRow ->
                                Utbetaling(
                                    fom = utbetalingRow.localDate("fom"),
                                    tom = utbetalingRow.localDate("tom"),
                                    grad = utbetalingRow.double("grad")
                                )
                            }.asList
                    )
                    Vedtak(
                        fødselsnummer = fnr,
                        førsteFraværsdag = row.localDate("forste_fravarsdag"),
                        utbetalinger = utbetalinger,
                        opprettet = row.localDateTime("opprettet")
                    )
                }.asList
            )
        }
}
