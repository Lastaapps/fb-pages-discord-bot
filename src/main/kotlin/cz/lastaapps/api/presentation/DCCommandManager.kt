package cz.lastaapps.api.presentation

import arrow.core.Either
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.api.DiscordKord
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.AppDCPermissionSet.Companion.stringify
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.error.text
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.id.toChannelID
import cz.lastaapps.api.domain.model.token.UserAccessToken
import cz.lastaapps.api.domain.usecase.AddPageUC
import cz.lastaapps.api.domain.usecase.GetAuthorizedPagesUC
import cz.lastaapps.api.domain.usecase.GetOAuthLink
import cz.lastaapps.api.domain.usecase.GetPagesForChannelUC
import cz.lastaapps.api.domain.usecase.ParsePageIDUC
import cz.lastaapps.api.domain.usecase.RemovePageUC
import cz.lastaapps.api.domain.usecase.SearchPagesUC
import cz.lastaapps.api.domain.usecase.VerifyUserPagesUC
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.application.GlobalChatInputCommand
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.string

class DCCommandManager(
    private val discordKord: DiscordKord,
    private val config: AppConfig,
    private val getAuthorizedPages: GetAuthorizedPagesUC,
    private val getPagesForChannelUC: GetPagesForChannelUC,
    private val searchPagesUC: SearchPagesUC,
    private val parsePageIDUC: ParsePageIDUC,
    private val addPageUC: AddPageUC,
    private val removePageUC: RemovePageUC,
    private val getOAuthLink: GetOAuthLink,
    private val verifyUserPages: VerifyUserPagesUC,
    private val managementRepo: ManagementRepo,
) {
    private val log = Logger.withTag("DCCommands")
    private val kord get() = discordKord.kord

    suspend fun register() {
        log.i { "Registering commands" }
        registerPing()
        registerListVerified()
        registerListLocal()
        if (config.facebook.enabledPublicContent) {
            registerSearchPages()
        }
        registerAddPage()
        registerRemovePage()
        if (config.facebook.enabledLogin) {
            registerAuthorizeLogin()
        }
        if (config.facebook.enabledUserTokens) {
            registerAuthorizeUserToken()
        }
    }

    private suspend fun registerPing() =
        kord.createGlobalChatInputCommand("fb_ping", "Ping pong (use for basic permissions testing)")
        { disableCommandInGuilds() }
            .toHandler {
                val channelId = interaction.channelId.toChannelID()
                val canAccess = managementRepo.checkAllPermissionKinds(channelId)
                when (canAccess) {
                    is Either.Right if canAccess.value.all { (_, value) -> value } ->
                        "All the permissions are set correctly."

                    is Either.Right ->
                        "Some permissions are missing, some (planned) action may fail: ${canAccess.value.stringify()}." +
                            " Only posting permissions are required now for the bot to work." +
                            " See the docs at https://github.com/LastaApps/fb-pages-discord-bot#permissions"

                    is Either.Left ->
                        "Failed to check permissions, contact developer, please."
                            .also { log.e(canAccess.value) { "Pink pong failed" } }
                }.let {
                    "FB pong\n$it\n"
                }
            }

    private suspend fun registerListVerified() =
        kord.createGlobalChatInputCommand("fb_list_available", "Lists pages that were verified and can be used")
        { disableCommandInGuilds() }
            .toHandler {
                when (val res = getAuthorizedPages()) {
                    is Either.Left -> "Internal error: ${res.value.text()}"
                    is Either.Right -> res.value
                        .toTable { "No pages are authorized yet.\n" }

                        // Public Content notice
                        .let {
                            if (config.facebook.enabledPublicContent) {
                                it + "Bot has also rights to monitor **any public page**.\n"
                            } else {
                                it
                            }
                        }
                }
            }

    private suspend fun registerListLocal() =
        kord.createGlobalChatInputCommand("fb_list_local", "Lists pages relayed into the current channel")
        { disableCommandInGuilds() }
            .toHandler {
                when (val res = getPagesForChannelUC(interaction.channelId.toChannelID())) {
                    is Either.Left -> "Internal error: ${res.value.text()}"
                    is Either.Right -> res.value
                        .toTable { "No pages are handled in this channel." }
                }
            }

    private suspend fun registerSearchPages() =
        kord.createGlobalChatInputCommand("fb_search_pages", "Search for pages on Facebook and show their ID") {
            string("name", "Name of the page to search for") {
                required = true
            }
            disableCommandInGuilds()
        }
            .toHandler {
                val name = interaction.command.strings["name"]!!
                when (val res = searchPagesUC(name)) {
                    is Either.Left -> "Internal error: ${res.value.text()}"
                    is Either.Right -> res.value
                        .toTable { "No pages found." }
                        .let { "Only pages (not groups) can be searched and used\n".plus(it) }
                }
            }

    private suspend fun registerAddPage() =
        kord.createGlobalChatInputCommand(
            "fb_add_page",
            "Adds a page to the current channel (ID, url, not name, comma separated list)",
        ) {
            string("page_id", "Page ID or link") {
                required = true
            }
            disableCommandInGuilds()
        }.toHandler {
            interaction.command.strings["page_id"]!!
                .split(",")
                .map {
                    val pageID = when (val res = parsePageIDUC(it)) {
                        is Either.Left -> {
                            return@toHandler "Failed to parse page ID: ${res.value.text()}."
                        }

                        is Either.Right -> res.value
                    }

                    when (val page = addPageUC(interaction.channelId.toChannelID(), pageID)) {
                        is Either.Left ->
                            "Failed to add page: ${page.value.text()}."

                        is Either.Right ->
                            "Page *${page.value.name}* added. Posts will be synced in at most ${config.interval}."
                    }
                }.joinToString("\n")
        }

    private suspend fun registerRemovePage() =
        kord.createGlobalChatInputCommand("fb_remove_page", "Removes a page from the current channel") {
            string("page_id", "Page ID or link") {
                required = true
            }
            disableCommandInGuilds()
        }.toHandler {
            interaction.command.strings["page_id"]!!
                .split(",")
                .map {
                    val pageID = when (val res = parsePageIDUC(it)) {
                        is Either.Left -> {
                            return@toHandler "Failed to parse page ID: ${res.value.text()}."
                        }

                        is Either.Right -> res.value
                    }

                    when (val page = removePageUC(interaction.channelId.toChannelID(), pageID)) {
                        is Either.Left ->
                            "Failed to remove page: ${page.value.text()}."

                        is Either.Right ->
                            "Page *${page.value.name}* removed."
                    }
                }.joinToString("\n")
        }

    private suspend fun registerAuthorizeLogin() =
        kord.createGlobalChatInputCommand("fb_authorize_login", "Open login dialog where you can authorize some apps")
        { disableCommandInGuilds() }
            .toHandler {
                "Use this [link](${getOAuthLink()}) to authorize your pages."
            }

    private suspend fun registerAuthorizeUserToken() =
        kord.createGlobalChatInputCommand(
            "fb_authorize_user",
            "Accepts (system) user token, authorizes all its pages",
        ) {
            string("user_token", "Generated (system) user token") {
                required = true
            }
            disableCommandInGuilds()
        }.toHandler {
            when (val pages = verifyUserPages(UserAccessToken(interaction.command.strings["user_token"]!!))) {
                is Either.Left -> "Failed to authorize system user: ${pages.value.text()}"
                is Either.Right ->
                    "Pages successfully authorized pages, you can add them now\n" + pages.value.toTable { "No pages authorized by this user" }
            }
        }

    private fun Collection<Page>.toTable(onEmpty: () -> String) =
        if (isNotEmpty()) {
            joinToString(
                prefix = "\nID\tNAME\tLINK\n",
                separator = "\n",
                postfix = "\n",
            ) {
                "`${it.fbId.id}`\t${it.name}\thttps://facebook.com/${it.fbId.id}"
            }
        } else {
            onEmpty() + "\n"
        }

    private fun GlobalChatInputCommand.toHandler(
        handle: suspend GuildChatInputCommandInteractionCreateEvent.(DeferredMessageInteractionResponseBehavior) -> String,
    ) {
        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            if (this@toHandler.id == interaction.invokedCommandId) {

                // Everyone sees the response
                // val response = interaction.deferPublicResponse()
                // Only the user sees the response
                val response = interaction.deferEphemeralResponse()

                handle(response)
                    .let { response.respond { content = it } }
            }
        }
    }
}
