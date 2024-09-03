package cz.lastaapps.api

import arrow.fx.coroutines.parMap
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

const val API_VERSION = "v20.0"

fun main() =
    runBlocking {
        println("Starting the bot")
        val config = AppConfig.fromEnv()
        val store = Store()
        val client = createHttpClient()
        val authAPI = AuthAPI(client, config)
        val dataAPI = DataAPI(client)

        println("Starting the server")
        setupServer(
            config,
            createOAuthURL = authAPI::createOAuthURL,
            handleOAuth = { params ->
                val userAccessToken = authAPI.exchangeOAuth(params)
                val pages = authAPI.grantAccess(userAccessToken)
                pages.forEach(store::storeAuthorizedPage)
            },
        )

        println("Starting Discord")
        val discordAPI = DiscordAPI(config)
        discordAPI.start(this)

        println("Done")
        println("-".repeat(80))
        // TODO https
        println("FB login address: http://${config.server.host}:${config.server.port}${config.server.endpointPublic}")

        delay(10.hours)
        while (true) {
            println("Starting collection...")
            val latestPostTimeStamp = Clock.System.now()

            store.loadPageDiscordPairs().forEach { (channelID, authorizedPages) ->
                authorizedPages
                    .parMap { authorizedPage ->
                        val posts = dataAPI.loadPagePosts(authorizedPage.id, authorizedPage.accessToken)
                        posts
                            .filter { it.createdAt > latestPostTimeStamp }
                            .parMap {
                                it to
                                    it.eventIDs().parMap { id ->
                                        dataAPI.loadEventData(id, authorizedPage.accessToken)
                                    }
                            }
                    }.flatten()
                    .sortedBy { it.first.createdAt }
                    .forEach {
                        discordAPI.postPostAndEvents(channelID, it)
                    }
            }

            println("Waiting for ${config.intervalSec.seconds}")
            delay(config.intervalSec.seconds)
        }
    }

fun setupServer(
    config: AppConfig,
    createOAuthURL: () -> String,
    handleOAuth: suspend (Parameters) -> Unit,
) {
    embeddedServer(
        CIO,
        host = config.server.host,
        port = config.server.port,
    ) {
        routing {
            get(config.server.endpointPublic) {
                call.respondRedirect(createOAuthURL(), permanent = false)
            }
            get(config.server.endpointOAuth) {
                handleOAuth(call.request.queryParameters)
                call.respond(
                    HttpStatusCode.OK,
                    "Access to your pages was granted. The bot administrator was automatically contacted and will add your page as soon as possible.",
                )
            }
        }
    }.start(wait = false)
}

private fun createHttpClient() =
    HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            level = LogLevel.BODY
        }
        install(DefaultRequest) {
            url(
                scheme = "https",
                host = "graph.facebook.com",
            )
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
    }
