val rapidsAndRiversVersion = "2025080710011754553680.051be9b54ef9"
val tbdLibsVersion = "2025.08.16-09.21-71db7cad"
val ktorVersion = "3.2.3"
val postgresqlVersion = "42.7.5"
val junitJupiterVersion = "5.12.1"
val jsonAssertVersion = "1.5.1"
val hikariCPVersion = "6.3.0"
val kotliqueryVersion="1.9.0"
val flywayVersion = "11.5.0" // Finnes en major update 🤔
val mockkVersion = "1.13.17"
val awaitilityVersion = "4.2.0"
val mainClass = "no.nav.helse.spokelse.AppKt"

plugins {
    kotlin("jvm") version "2.2.10"
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("com.github.navikt.tbd-libs:naisful-app:$tbdLibsVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")

    testImplementation("org.awaitility:awaitility:$awaitilityVersion")

    testImplementation("com.github.navikt.tbd-libs:postgres-testdatabaser:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:naisful-test-app:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:signed-jwt-issuer-test:$tbdLibsVersion")
    testImplementation("org.skyscreamer:jsonassert:$jsonAssertVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

repositories {
    val githubPassword: String? by project
    mavenCentral()
    /* ihht. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
        så plasseres github-maven-repo (med autentisering) før nav-mirror slik at github actions kan anvende førstnevnte.
        Det er fordi nav-mirroret kjører i Google Cloud og da ville man ellers fått unødvendige utgifter til datatrafikk mellom Google Cloud og GitHub
     */
    maven {
        url = uri("https://maven.pkg.github.com/navikt/maven-release")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("21"))
    }
}

tasks {

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        val parallellDisabled = System.getenv("CI" ) == "true"
        systemProperty("junit.jupiter.execution.parallel.enabled", parallellDisabled.not().toString())
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
    }

    named<Jar>("jar") {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClass
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists()) it.copyTo(file)
            }
        }
    }
}
