package cz.lastaapps.api.data.api

import co.touchlab.kermit.Logger
import cz.lastaapps.api.presentation.AppConfig
import dev.kord.core.Kord
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@JvmInline
value class DiscordKord private constructor(val kord: Kord) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            log.d { "Starting the DC client..." }
            kord.login()
        }
    }

    companion object {
        private val log = Logger.Companion.withTag("DiscordKord")

        suspend fun create(
            config: AppConfig,
            baseClient: HttpClient,
        ): DiscordKord {
            val kord = Kord(config.discord.token) {
                // Sets up Logging for free
                httpClient = baseClient.config {
                    // Copied from KordBuilderUtil
                    expectSuccess = false
                    val json = Json {
                        encodeDefaults = false
                        allowStructuredMapKeys = true
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                    install(ContentNegotiation) { json(json) }
                    install(WebSockets)
                }
            }
            return DiscordKord(kord)
        }
    }

}
