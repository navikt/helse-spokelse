package no.nav.helse.spokelse.tbdutbetaling

import no.nav.helse.rapids_rivers.JsonMessage
import java.time.LocalDateTime

internal class Melding(private val melding: String) {
    internal constructor(packet: JsonMessage): this(packet.toString())
    internal val tidspunkt: LocalDateTime = LocalDateTime.now() // TODO: hmm
    override fun toString() = melding
}
