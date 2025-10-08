import java.util.Properties

plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sentry)
    alias(libs.plugins.shadow)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.versionCatalogUpdate)
}

group = "cz.lastaapps"
version = "1.0"

application {
    mainClass.set("cz.lastaapps.api.MainKt")
    // mainClass.set("cz.lastaapps.scraping.MainKt")
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
    sourceSets.all {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
            optIn("kotlin.time.ExperimentalTime")
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("cz.lastaapps.api")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bundles.kord)

    implementation(libs.skrapeit)
    // fix security vulnerabilities in skrapeit libs
    implementation(libs.bundles.skrapeit.overrides)

    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)

    implementation(libs.kermit)

    implementation(platform(libs.arrow.bom))
    implementation(libs.bundles.arrow)

    implementation(libs.sqldelight.driver.sqlite)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    testImplementation(libs.bundles.koin.test)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.kotest)
}

tasks.test {
    useJUnitPlatform()
}

versionCatalogUpdate {
    sortByKey.set(true)
    pin {}
    keep {
        keepUnusedVersions.set(true)
    }
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = true

    org = "lasta-apps"
    projectName = "fb-pages-discord-bot"

    val sentryAuthToken = localProperties.getProperty("sentry.authToken")
        ?: project.findProperty("sentry.authToken") as? String
        ?: error("Please, specify sentry.authToken property")

    authToken = sentryAuthToken
}

tasks.named("generateSentryBundleIdJava") {
    dependsOn(tasks.named("generateMainDatabaseInterface"))
}
