plugins {
    val kotlinVersion = "2.2.0"

    application
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("app.cash.sqldelight") version "2.1.0"
}

group = "cz.lastaapps"
version = "1.0"

application {
    mainClass.set("cz.lastaapps.api.MainKt")
//    mainClass.set("cz.lastaapps.scraping.MainKt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xwhen-guards",
            "-Xcontext-parameters",
            "-Xcontext-sensitive-resolution",
            "-Xannotation-target-all",
            "-Xnested-type-aliases",
            "-Xannotation-default-target=param-property",
        )
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
    }
}

repositories {
    mavenCentral()
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("cz.lastaapps.api")
        }
    }
}

// I'm to lazy to setup catalogs
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    val kordVersion = "0.15.0"
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("dev.kord:kord-common:$kordVersion")
    implementation("dev.kord:kord-rest:$kordVersion")
    // for some reason, sometimes required to run the app
    // this was not required before, so it's probably just some cache error
    // implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")

    implementation("it.skrape:skrapeit:1.3.0-alpha.2")
    // fix security vulnerabilities in skrapeit libs
    implementation("ch.qos.logback:logback-core:1.5.18")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("commons-net:commons-net:3.11.1")
    implementation("org.apache.commons:commons-text:1.14.0")
    implementation("org.jsoup:jsoup:1.21.1")
    implementation("xalan:xalan:2.7.3")

    val ktorVersion = "3.2.3"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("co.touchlab:kermit:2.0.6")

    implementation(platform("io.arrow-kt:arrow-stack:2.1.2"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")
    implementation("io.arrow-kt:arrow-resilience")

    implementation("app.cash.sqldelight:sqlite-driver:2.1.0")

    implementation(platform("io.insert-koin:koin-bom:4.1.0"))
    implementation("io.insert-koin:koin-core")
    testImplementation("io.insert-koin:koin-test")
    testImplementation("io.insert-koin:koin-test-junit5")

    testImplementation(kotlin("test"))
    val kotestVersion = "5.9.1"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

tasks.test {
    useJUnitPlatform()
}
