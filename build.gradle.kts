plugins {
    alias(libs.plugins.sykepenger.deployable)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.spokelse.AppKt"
    imageName = "helse-spokelse"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.tbdLibs.naisfulApp)
    implementation(libs.ktor.server.auth.jwt) {
        exclude(group = "junit")
    }

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotliquery)

    testImplementation(libs.mockk)

    testImplementation(libs.awaitility)

    testImplementation(libs.tbdLibs.postgresTestdatabaser)
    testImplementation(libs.tbdLibs.naisfulTestApp)
    testImplementation(libs.tbdLibs.signedJwtIssuerTest)
    testImplementation(libs.jsonassert)
}

tasks {
    withType<Test> {
        val parallellDisabled = System.getenv("CI") == "true"
        systemProperty("junit.jupiter.execution.parallel.enabled", parallellDisabled.not().toString())
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
    }
}
