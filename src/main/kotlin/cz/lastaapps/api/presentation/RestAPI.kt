package cz.lastaapps.api.presentation

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.api.FBAuthAPI
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.AppDCPermissionSet.Companion.stringify
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.error.respondError
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.usecase.AddPageUC
import cz.lastaapps.api.domain.usecase.ChangeChannelEnabledUC
import cz.lastaapps.api.domain.usecase.GetAuthorizedPagesUC
import cz.lastaapps.api.domain.usecase.RemovePageUC
import cz.lastaapps.api.domain.usecase.RunJobsUC
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
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue
import kotlinx.coroutines.delay

class RestAPI(
    private val config: AppConfig,
    private val repository: ManagementRepo,
    private val authApi: FBAuthAPI,
    private val addPageUC: AddPageUC,
    private val removePageUC: RemovePageUC,
    private val updateChannelEnabledUC: ChangeChannelEnabledUC,
    private val getAuthorizedPages: GetAuthorizedPagesUC,
    private val runJobsUC: RunJobsUC,
) {
    private val log = Logger.withTag("RestAPI")

    fun oauthUserEndpoint() = "${config.server.hostURL}${config.server.endpointPublic}"

    fun setup() {
        log.i { "Starting the Ktor API server" }
        setupServer(
            config,
            routing = {
                get(config.server.endpointPublic) {
                    call.respondRedirect(authApi.createOAuthURL(), permanent = false)
                }
                get(config.server.endpointOAuth) {
                    val params = call.request.queryParameters
                    val userAccessToken = authApi.exchangeOAuth(params).fold(
                        {
                            log.e(it) { "Failed to exchange OAuth" }
                            call.respondError(it)
                            return@get
                        },
                        { it },
                    )
                    val pages = authApi.grantAccessToUserPages(userAccessToken).getOrNull()!!
                    pages.forEach(repository::storeAuthorizedPage)
                    call.respond(
                        HttpStatusCode.OK,
                        "Access to your pages was successfully granted. Please, contact the bot administrator now, so the page can be linked to the appropriate Discord channel.",
                    )
                }
                route("/admin") {
                    install(AuthorizationPlugin(config))

                    post("/channel-page/{channel_id}/{page_id}") {
                        addPageUC(
                            call.parameters["channel_id"]!!.toULong().let(::DCChannelID),
                            call.parameters["page_id"]!!.toULong().let(::FBPageID),
                        ).onLeft {
                            call.respondError(it)
                        }.onRight {
                            call.respond(HttpStatusCode.Created)
                        }
                    }
                    delete("/channel-page/{channel_id}/{page_id}") {
                        removePageUC(
                            call.parameters["channel_id"]!!.toULong().let(::DCChannelID),
                            call.parameters["page_id"]!!.toULong().let(::FBPageID),
                        ).onLeft {
                            call.respondError(it)
                        }.onRight {
                            call.respond(HttpStatusCode.Accepted)
                        }
                    }
                    put("/channel/{channel_id}/enabled/{enable}") {
                        updateChannelEnabledUC(
                            call.parameters["channel_id"]!!.toULong().let(::DCChannelID),
                            call.parameters["enable"]!!.toBoolean(),
                        ).onLeft {
                            call.respondError(it)
                        }.onRight {
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                    get("/state") {
                        buildString {
                            append("All authorized pages:\n")
                            getAuthorizedPages()
                                .getOrNull()
                                .orEmpty()
                                .forEach {
                                    append("> ")
                                    append(it.fbId.id)
                                    append('\t')
                                    append(it.name)
                                    append('\n')
                                }
                            append('\n')
                            append("Assigned pages:\n")
                            repository.loadChannelsWithInfo()
                                .forEach { (dbId, dcId, name, channelEnabled, serverName) ->
                                    val pages = repository.loadAuthorizedPagesForChannel(dbId)
                                    val permissions = repository.checkAllPermissionKinds(dcId).getOrNull()

                                    append("> ")
                                    append(name)
                                    append(' ')
                                    append("(DC: ${dcId.id})")
                                    append(' ')
                                    append("(DB: ${dbId.id})")
                                    append(' ')
                                    append("(server: ${serverName.getOrNull()})")
                                    append(' ')
                                    append("(${if (channelEnabled) "enabled" else "disabled"})")
                                    append(' ')
                                    append("(permissions: ${permissions?.stringify()})")
                                    append('\n')

                                    // yes, this should happen only once in one query and I also do it like that elsewhere,
                                    // but I need to fix my architecture first before I can access it
                                    pages.getOrNull()?.also {
                                        if (it.isEmpty()) {
                                            append("\tNo pages\n")
                                        }
                                    }?.forEach { page ->
                                        append("\t")
                                        append(page.accessToken.token)
                                        append("\t | \t")
                                        append(page.name)
                                        append("\t | \t")
                                        append("https://facebook.com/${page.fbId.id}")
                                        append("\n")
                                    } ?: append("\tNull pages\n")
                                }
                        }.let { call.respond(HttpStatusCode.OK, it) }
                    }
                    post("/run-jobs") {
                        runJobsUC()
                        call.respond(HttpStatusCode.Created, "New job scheduled")
                    }
                }
            },
        )
    }

    private fun setupServer(
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
}
