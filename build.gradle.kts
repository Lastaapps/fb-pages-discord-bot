plugins {
    application
    kotlin("jvm") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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

// I'm to lazy to setup catalogs
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")

    val kordVersion = "0.14.0"
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("dev.kord:kord-common:$kordVersion")
    implementation("dev.kord:kord-rest:$kordVersion")

    implementation("it.skrape:skrapeit:1.3.0-alpha.2")
    // fix security vulnerabilities in skrapeit libs
    implementation("ch.qos.logback:logback-core:1.4.14")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("commons-net:commons-net:3.9.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("xalan:xalan:2.7.3")

    val ktorVersion = "2.3.11"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation(platform("io.arrow-kt:arrow-stack:1.2.4"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")

    testImplementation(kotlin("test"))
    val kotestVersion = "5.9.0"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

tasks.test {
    useJUnitPlatform()
}

