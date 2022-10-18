package no.nav.helse.spokelse.tbdutbetaling

internal class Annullering(
    internal val arbeidsgiverFagsystemId: String?,
    internal val personFagsystemId: String?
) {
    init {
        require(arbeidsgiverFagsystemId != null || personFagsystemId != null) {
            "Enten arbeidsgiverFagsystemId eller personFagsystemId må være satt "
        }
    }
}
