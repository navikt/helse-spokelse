plugins {
    alias(libs.plugins.sykepenger.deployable)
    alias(libs.plugins.kotlin.serialization)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.spokelse.AppKt"
    imageName = "helse-spokelse"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.tbdLibs.naisfulApp)
    implementation(libs.tbdLibs.accessTokenProviderApi)
    implementation(libs.tbdLibs.accessTokenProviderTexas)
    implementation(libs.tbdLibs.populasjonstilgangskontrollApi)
    implementation(libs.tbdLibs.populasjonstilgangskontrollTilgangsmaskinen)
    implementation(libs.sykepengerLibs.logging)
    implementation(libs.logback.syslog4j)
    implementation(libs.ktor.server.auth.jwt) {
        exclude(group = "junit")
    }

    implementation(libs.bundles.smiley4.ktor.openapi.tools)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotliquery)

    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)

    testImplementation(libs.awaitility)

    testImplementation(testFixtures(libs.sykepengerLibs.postgres))
    testImplementation(libs.sykepengerLibs.testing)
    testImplementation(libs.tbdLibs.naisfulTestApp)
    testImplementation(libs.tbdLibs.rapidsAndRiversTest)
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
        systemProperty("oppdaterOpenApiSnapshot", System.getProperty("oppdaterOpenApiSnapshot", "false"))
    }
}
