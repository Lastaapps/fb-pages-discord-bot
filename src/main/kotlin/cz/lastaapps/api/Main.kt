package cz.lastaapps.api

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.AppDatabase
import cz.lastaapps.api.data.api.DiscordKord
import cz.lastaapps.api.data.repo.ProcessingRepo
import cz.lastaapps.api.di.diModule
import cz.lastaapps.api.presentation.AppConfig
import cz.lastaapps.api.presentation.DCCommandManager
import cz.lastaapps.api.presentation.RestAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

const val API_VERSION = "v22.0"
private val log by lazy { Logger.withTag("Main") }

fun main() =
    runBlocking {
        log.i { "Starting the bot" }

        val config = AppConfig.fromEnv()
        Logger.setMinSeverity(config.logging.logLevel)

        log.i { "Starting Sentry" }
        configureSentry(config.logging.sentryDsn)
        Logger.addLogWriter(SentryLogWriter())

        log.i { "Starting DI - Koin" }
        startKoin { modules(diModule) }
        val koin = get()
        koin.loadModules(listOf(module { single { this@runBlocking } bind CoroutineScope::class }))
        koin.loadModules(listOf(module { single { config } }))
        koin.loadModules(listOf(module { single { AppDatabase.create(get<AppConfig>()) } }))

        log.i { "Starting Discord" }
        val discordKord = DiscordKord.create(config, koin.get())
        discordKord.start(this)
        koin.loadModules(listOf(module { single { discordKord } }))

        log.d { "Setting up presentation" }
        koin.get<DCCommandManager>().register()
        koin.get<RestAPI>().setup()

        log.i { "Initialization done, enabled modules:" }
        log.i { "-".repeat(80) }
        if (config.facebook.enabledPublicContent) {
            log.i { "FB page public content feature" }
        }
        if (config.facebook.enabledLogin) {
            log.i { "FB login: ${koin.get<RestAPI>().oauthUserEndpoint()}" }
        }
        if (config.facebook.enabledUserTokens) {
            log.i { "FB user tokens" }
        }
        log.i { "-".repeat(80) }

        // starts posts fetching
        koin.get<ProcessingRepo>().requestNow()
    }

