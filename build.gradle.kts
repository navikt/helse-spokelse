val ktorVersion = "1.5.0"
val wireMockVersion = "2.27.1"
val junitJupiterVersion = "5.6.2"
val mainClass = "no.nav.helse.spokelse.AppKt"

plugins {
    kotlin("jvm") version "1.5.30"
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:1.5e3ca6a")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("no.nav:vault-jdbc:1.3.7")
    implementation("org.flywaydb:flyway-core:6.5.0")
    implementation("com.github.seratch:kotliquery:1.3.1")

    testImplementation("io.mockk:mockk:1.10.0")
    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("no.nav:kafka-embedded-env:2.4.0")
    testImplementation("org.awaitility:awaitility:4.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

repositories {
    jcenter()
    maven("https://jitpack.io")
    maven("https://packages.confluent.io/maven/")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "16"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "16"
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
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
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
}
