package no.nav.helse.spokelse

import java.time.LocalDate
import java.util.*

class Vedtak(
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Double
)
