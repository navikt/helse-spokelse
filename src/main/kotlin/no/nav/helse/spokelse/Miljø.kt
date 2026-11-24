package no.nav.helse.spokelse

internal enum class Miljø {
    DEV,
    PROD,
    ;

    internal companion object {
        internal fun fra(env: Map<String, String>) =
            when (val cluster = env["NAIS_CLUSTER_NAME"]) {
                "dev-gcp" -> DEV
                "prod-gcp" -> PROD
                else -> error("Ukjent NAIS_CLUSTER_NAME: $cluster")
            }
    }
}
