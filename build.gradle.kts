import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val slf4jVersion = "1.7.25"
val ktorVersion = "1.2.4"
val jacksonVersion = "2.9.8"
val kafkaVersion = "2.3.1"
val prometheusVersion = "0.6.0"
val micrometerRegistryPrometheusVersion = "1.1.5"
val wireMockVersion = "2.19.0"
val mockkVersion = "1.9.3.kotlin12"
val junitJupiterVersion = "5.4.0"
val mainClass = "no.nav.helse.sparkel.sykepengeperioder.AppKt"

plugins {
    kotlin("jvm") version "1.3.60"
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

dependencies {
    implementation("no.nav.helse:rapids-rivers:1.40300fd")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("no.nav:kafka-embedded-env:2.3.0")
    testImplementation("org.awaitility:awaitility:4.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("http://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/helse-spleis")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

tasks.named<Jar>("jar") {
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

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions.jvmTarget = "12"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "12"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.6.2"
}
