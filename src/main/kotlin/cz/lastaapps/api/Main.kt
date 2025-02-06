package cz.lastaapps.api

import arrow.core.Either
import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import co.touchlab.kermit.SystemWriter
import cz.lastaapps.api.data.DiscordAPI
import cz.lastaapps.api.data.DiscordKord
import cz.lastaapps.api.data.FBAuthAPI
import cz.lastaapps.api.data.FBDataAPI
import cz.lastaapps.api.data.Repository
import cz.lastaapps.api.data.TimeStampFormatter
import cz.lastaapps.api.data.isPostPosted
import cz.lastaapps.api.di.diModule
import cz.lastaapps.api.presentation.AppConfig
import cz.lastaapps.api.presentation.DCCommandManager
import io.ktor.http.HttpStatusCode
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
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

const val API_VERSION = "v22.0"
private val log by lazy { Logger.withTag("Main") }

fun main() =
    runBlocking {
        Logger.setLogWriters(SystemWriter(TimeStampFormatter))
        log.i { "Starting the bot" }

        startKoin { modules(diModule) }
        val koin = get()

        val config = AppConfig.fromEnv()
        koin.loadModules(listOf(module { single { config } }))

        log.i { "Starting Discord" }
        val discordKord = DiscordKord.create(config)
        discordKord.start(this)
        koin.loadModules(listOf(module { single { discordKord } }))

        koin.get<DCCommandManager>().register()

        val repository = koin.get<Repository>()
        val FBAuthAPI = koin.get<FBAuthAPI>()
        val FBDataAPI = koin.get<FBDataAPI>()

        val discordAPI = koin.get<DiscordAPI>()
        return@runBlocking

        log.i { "Starting the server" }
        setupServer(
            config,
            routing = {
                get(config.server.endpointPublic) {
                    call.respondRedirect(FBAuthAPI.createOAuthURL(), permanent = false)
                }
                get(config.server.endpointOAuth) {
                    val params = call.request.queryParameters
                    val userAccessToken = FBAuthAPI.exchangeOAuth(params)
                    val pages = FBAuthAPI.grantAccessToUserPages(userAccessToken).getOrNull()!!
//                    pages.forEach(repository::storeAuthorizedPage) call.respond(
//                        HttpStatusCode.OK,
//                        "Access to your pages was successfully granted. Please, contact the bot administrator now, so the page can be linked to the appropriate Discord channel.",
//                    )
                }
                route("/admin") {
                    install(AuthorizationPlugin(config))

                    post("/channel-page/{channel_id}/{page_id}") {
//                        repository.createChannelPageRelation(
//                            call.parameters["channel_id"]!!,
//                            call.parameters["page_id"]!!,
//                        )
                        call.respond(HttpStatusCode.Created)
                    }
                    delete("/channel-page/{channel_id}/{page_id}") {
//                        repository.removeChannelPageRelation(
//                            call.parameters["channel_id"]!!,
//                            call.parameters["page_id"]!!,
//                        )
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/state") {
                        buildString {
                            append("All authorized pages:\n")
                            repository.loadAuthorizedPages().forEach {
                                append("> ")
                                append(it.toString())
                                append('\n')
                            }
                            append('\n')
                            append("Assigned pages:\n")
                            repository.loadPageDiscordPairs().forEach { (key, value) ->
                                append("> ")
                                append(discordAPI.getChannelName(key))
                                append(" \t")
                                append(key)
                                append(": \t")
                                append(value.map { it.fbId to it.name })
                                append("\n")
                            }
                        }.let { call.respond(HttpStatusCode.OK, it) }
                    }
                }
            },
        )

        log.i { "Initialization done" }
        log.i { "-".repeat(80) }
        log.i { "FB login address: ${config.server.hostURL}${config.server.endpointPublic}" }
        log.i { "-".repeat(80) }

        if (config.setupMode) {
            log.i { "The server is run in setup mode, nothing more to do" }
            return@runBlocking
        }

        while (true) {
            log.i { "Starting collection..." }
            processBatch(repository, FBDataAPI, discordAPI)
            log.i { "Waiting for ${config.intervalSec.seconds}" }
            delay(config.intervalSec.seconds)
        }
    }

private suspend fun processBatch(
    repository: Repository,
    FBDataAPI: FBDataAPI,
    discordAPI: DiscordAPI,
) {
    val concurrency = 3

    repository.loadPageDiscordPairs().forEach { (channelID, authorizedPages) ->
        authorizedPages
            .map { authorizedPage ->
                Either
                    .catch {
                        val posts = FBDataAPI.loadPagePosts(authorizedPage.fbId, authorizedPage.accessToken)
                        posts
                            .filter { it.canBePublished() }
                            .filterNot { repository.isPostPosted(channelID, it.id) }
                            .parMap(concurrency = concurrency) { post ->
                                Triple(
                                    authorizedPage,
                                    post,
                                    post.eventIDs().parMap(concurrency = concurrency) { id ->
                                        FBDataAPI.loadEventData(id, authorizedPage.accessToken)
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
                repository.createMessagePostRelation(channelID, messageID, it.second.id)
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
