plugins {
    val kotlinVersion = "2.0.20"

    application
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("app.cash.sqldelight") version "2.0.2"
}

group = "cz.lastaapps"
version = "1.0-SNAPSHOT"

application {
    // mainClass.set("cz.lastaapps.api.MainKt")
    mainClass.set("cz.lastaapps.scraping.MainKt")
}

kotlin {
    jvmToolchain(21)
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
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    val kordVersion = "0.14.0"
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("dev.kord:kord-common:$kordVersion")
    implementation("dev.kord:kord-rest:$kordVersion")

    implementation("it.skrape:skrapeit:1.3.0-alpha.2")
    // fix security vulnerabilities in skrapeit libs
    implementation("ch.qos.logback:logback-core:1.5.7")
    implementation("ch.qos.logback:logback-classic:1.5.7")
    implementation("commons-net:commons-net:3.11.1")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("xalan:xalan:2.7.3")

    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    implementation(platform("io.arrow-kt:arrow-stack:1.2.4"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")

    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")

    testImplementation(kotlin("test"))
    val kotestVersion = "5.9.1"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

tasks.test {
    useJUnitPlatform()
}
