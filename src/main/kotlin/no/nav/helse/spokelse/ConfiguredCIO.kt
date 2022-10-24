package no.nav.helse.spokelse

import io.ktor.server.cio.*
import io.ktor.server.engine.*

internal object ConfiguredCIO: ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    // Denne er default `Runtime.getRuntime().availableProcessors()` som gir 3 i prod.
    // -> Gjeldende config er da connectionGroupSize=2, workerGroupSize=2, callGroupSize=3
    // -> Nå connectionGroupSize=9, workerGroupSize=9, callGroupSize=16
    private const val customParallelism = 16

    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: CIOApplicationEngine.Configuration.() -> Unit
    ): CIOApplicationEngine {
        return CIOApplicationEngine(environment) {
            connectionGroupSize = customParallelism / 2 + 1
            workerGroupSize  = customParallelism / 2 + 1
            callGroupSize = customParallelism
        }
    }
}
