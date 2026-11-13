package no.nav.helse.spokelse

import kotliquery.sessionOf
import no.nav.helse.spokelse.felles.asSQL
import no.nav.helse.spokelse.felles.update
import java.util.*
import javax.sql.DataSource

class DokumentDao(
    val datasource: DataSource,
) {
    fun opprett(hendelse: Hendelse) {
        sessionOf(datasource).use { session ->
            asSQL(
                "INSERT INTO hendelse(hendelse_id, dokument_id, type) VALUES (:hendelseId, :dokumentId, :type) ON CONFLICT DO NOTHING",
                "hendelseId" to hendelse.hendelseId,
                "dokumentId" to hendelse.dokumentId,
                "type" to hendelse.type.name,
            ).update(session)
        }
    }

    fun lagre(
        vedtaksperiodeId: UUID,
        fagsystemId: String,
    ) {
        sessionOf(datasource).use { session ->
            asSQL(
                "INSERT INTO vedtak_utbetalingsref(vedtaksperiode_id, utbetalingsref) VALUES (:vedtaksperiodeId, :fagsystemId) ON CONFLICT DO NOTHING",
                "vedtaksperiodeId" to vedtaksperiodeId,
                "fagsystemId" to fagsystemId,
            ).update(session)
        }
    }
}
