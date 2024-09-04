package cz.lastaapps.api

import arrow.core.Either
import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

const val API_VERSION = "v20.0"
private val log = Logger.withTag("Main")

fun main() =
    runBlocking {
        log.i { "Starting the bot" }
        val config = AppConfig.fromEnv()
        val store = Store(config)
        val client = createHttpClient()
        val authAPI = AuthAPI(client, config)
        val dataAPI = DataAPI(client)

        log.i { "Starting Discord" }
        val discordAPI = DiscordAPI.create(config, client)
        discordAPI.start(this)

        log.i { "Starting the server" }
        setupServer(
            config,
            routing = {
                get(config.server.endpointPublic) {
                    call.respondRedirect(authAPI.createOAuthURL(), permanent = false)
                }
                get(config.server.endpointOAuth) {
                    val params = call.request.queryParameters
                    val userAccessToken = authAPI.exchangeOAuth(params)
                    val pages = authAPI.grantAccess(userAccessToken)
                    pages.forEach(store::storeAuthorizedPage)
                    call.respond(
                        HttpStatusCode.OK,
                        "Access to your pages was granted. Please, contact the bot administrator that you finished the registration process, so the page can be linked to appropriate Discord channel channel.",
                    )
                }
                route("/admin") {
                    install(AuthorizationPlugin(config))

                    post("/channel-page/{channel_id}/{page_id}") {
                        store.createChannelPageRelation(
                            call.parameters["channel_id"]!!,
                            call.parameters["page_id"]!!,
                        )
                        call.respond(HttpStatusCode.Created)
                    }
                    delete("/channel-page/{channel_id}/{page_id}") {
                        store.removeChannelPageRelation(
                            call.parameters["channel_id"]!!,
                            call.parameters["page_id"]!!,
                        )
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/state") {
                        buildString {
                            append("All authorized pages:\n")
                            store.loadAuthenticatedPages().forEach {
                                append("> ")
                                append(it.toString())
                                append('\n')
                            }
                            append('\n')
                            append("Assigned pages:\n")
                            store.loadPageDiscordPairs().forEach { (key, value) ->
                                append("> ")
                                append(discordAPI.getChannelName(key))
                                append(" \t")
                                append(key)
                                append(": \t")
                                append(value.map { it.id to it.name })
                                append("\n")
                            }
                        }.let { call.respond(HttpStatusCode.OK, it) }
                    }
                }
            },
        )

        log.i { "Initialization done" }
        log.i { "-".repeat(80) }
        // TODO https
        log.i { "FB login address: http://${config.server.host}:${config.server.port}${config.server.endpointPublic}" }
        log.i { "-".repeat(80) }

        if (config.setupMode) {
            log.i { "The server is run in setup mode, nothing more to do" }
            return@runBlocking
        }

        while (true) {
            log.i { "Starting collection..." }
            processBatch(store, dataAPI, discordAPI)
            log.i { "Waiting for ${config.intervalSec.seconds}" }
            delay(config.intervalSec.seconds)
        }
    }

private suspend fun processBatch(
    store: Store,
    dataAPI: DataAPI,
    discordAPI: DiscordAPI,
) {
    val concurrency = 3

    store.loadPageDiscordPairs().forEach { (channelID, authorizedPages) ->
        authorizedPages
            .map { authorizedPage ->
                Either
                    .catch {
                        val posts = dataAPI.loadPagePosts(authorizedPage.id, authorizedPage.accessToken)
                        posts
                            .filter { it.canBePublished() }
                            .filterNot { store.isPostPosted(it.id) }
                            .parMap(concurrency = concurrency) { post ->
                                Triple(
                                    authorizedPage,
                                    post,
                                    post.eventIDs().parMap(concurrency = concurrency) { id ->
                                        dataAPI.loadEventData(id, authorizedPage.accessToken)
                                    },
                                )
                            }
                    }.onLeft { it.printStackTrace() }
                    .fold({ emptyList() }, { it })
                    .filter { it.third.all { event -> event.canBePublished() } }
            }.flatten()
            .sortedBy { it.second.createdAt }
            .forEach {
                log.i { "Posting ${it.second} to $channelID" }
                val messageID = discordAPI.postPostAndEvents(channelID, it)
                store.createMessagePostRelation(messageID, it.second.id)
            }
    }
}

fun setupServer(
    config: AppConfig,
    routing: Routing.() -> Unit,
) {
    embeddedServer(
        CIO,
        host = config.server.host,
        port = config.server.port,
    ) {
        // Yes, the errors should not be shared, but whatever...
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                cause.printStackTrace()
                call.respondText(
                    text = "500: $cause",
                    status = HttpStatusCode.InternalServerError,
                )
            }
        }
        routing(routing)
    }.start(wait = false)
}

private fun createHttpClient() =
    HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            logger =
                object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        log.v { message.replace("\n", "\\n") }
                    }
                }
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

@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun AuthorizationPlugin(config: AppConfig) =
    createRouteScopedPlugin(
        name = "AuthorizationPlugin",
    ) {
        pluginConfig.apply {
            on(AuthenticationChecked) { call ->
                val isValid =
                    measureTimedValue {
                        call.parameters["access_token"] == config.adminToken
                    }.also { delay(1.milliseconds - it.duration) }
                        .value

                if (!isValid) {
                    call.respondText(
                        "You are not allowed to access this endpoint.",
                        status = HttpStatusCode.Forbidden,
                    )
                }
            }
        }
    }
