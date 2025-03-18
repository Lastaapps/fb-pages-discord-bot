package cz.lastaapps.api.data.api

import co.touchlab.kermit.Logger
import cz.lastaapps.api.presentation.AppConfig
import dev.kord.core.Kord
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.DefaultGateway
import dev.kord.gateway.retry.LinearRetry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration.Companion.seconds
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
                this.defaultStrategy = EntitySupplyStrategy.cacheWithRestFallback
                this.gateways { resources, shards ->
//                    val rateLimiter =
//                        IdentifyRateLimiter(resources.maxConcurrency, defaultDispatcher)
                    shards.map {
                        DefaultGateway {
                            client = resources.httpClient
//                            identifyRateLimiter = rateLimiter
//                            this.sendRateLimiter = RequestResponse.BucketRateLimit
                            this.reconnectRetry = LinearRetry(10.seconds, 120.seconds, 60)
                        }
                    }
                }
            }
            return DiscordKord(kord)
        }
    }

}
