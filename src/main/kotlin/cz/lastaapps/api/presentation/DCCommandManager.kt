package cz.lastaapps.api.presentation

import arrow.core.Either
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.DiscordKord
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
import cz.lastaapps.api.domain.usecase.VerifyUserPagesUC
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
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
    private val parsePageIDUC: ParsePageIDUC,
    private val addPageUC: AddPageUC,
    private val removePageUC: RemovePageUC,
    private val getOAuthLink: GetOAuthLink,
    private val verifyUserPages: VerifyUserPagesUC,
) {
    private val log = Logger.withTag("DCCommands")
    private val kord get() = discordKord.kord

    suspend fun register() {
        log.i { "Registering commands" }
        registerListVerified()
        registerListLocal()
        registerAddPage()
        registerRemovePage()
        if (config.facebook.enabledLogin) {
            registerAuthorizeLogin()
        }
        if (config.facebook.enabledSystemUser) {
            registerAuthorizeSystemUser()
        }
    }

    private suspend fun registerListVerified() =
        kord.createGlobalChatInputCommand("fb_list_available", "Lists pages that were verified and can be used")
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
            .toHandler {
                when (val res = getPagesForChannelUC(interaction.channelId.toChannelID())) {
                    is Either.Left -> "Internal error: ${res.value.text()}"
                    is Either.Right -> res.value
                        .toTable { "No pages are handled in this channel." }
                }
            }

    private suspend fun registerAddPage() =
        kord.createGlobalChatInputCommand("fb_add_page", "Adds a page to the current channel (ID, url, not name)") {
            string("page_id", "Page ID or link") {
                required = true
            }
        }.toHandler {
            val pageID = when (val res = parsePageIDUC(interaction.command.strings["page_id"]!!)) {
                is Either.Left -> {
                    return@toHandler "Failed to parse page ID: ${res.value.text()}."
                }

                is Either.Right -> res.value
            }

            when (val page = addPageUC(interaction.channelId.toChannelID(), pageID)) {
                is Either.Left ->
                    "Failed to add page: ${page.value.text()}."

                is Either.Right ->
                    "Page *${page.value.name}* added."
            }
        }

    private suspend fun registerRemovePage() =
        kord.createGlobalChatInputCommand("fb_remove_page", "Removes a page from the current channel") {
            string("page_id", "Page ID or link") {
                required = true
            }
        }.toHandler {
            val pageID = when (val res = parsePageIDUC(interaction.command.strings["page_id"]!!)) {
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
        }

    private suspend fun registerAuthorizeLogin() =
        kord.createGlobalChatInputCommand("fb_authorize_login", "Open login dialog where you can authorize some apps")
            .toHandler {
                "Use this [link](${getOAuthLink()}) to authorize your pages."
            }

    private suspend fun registerAuthorizeSystemUser() =
        kord.createGlobalChatInputCommand("fb_authorize_system_user", "Accepts system user, authorizes all its pages") {
            string("system_user_token", "Generated system user token") {
                required = true
            }
        }.toHandler {
            when (val pages = verifyUserPages(UserAccessToken(interaction.command.strings["system_user_token"]!!))) {
                is Either.Left -> "Failed to authorize system user: ${pages.value.text()}"
                is Either.Right ->
                    "Pages successfully authorized pages, you can add them now\n" + pages.value.toTable { "No pages authorized by this user" }
            }
        }

    private fun Collection<Page>.toTable(onEmpty: () -> String) =
        if (isNotEmpty()) {
            joinToString(
                prefix = "```\nID\tNAME\n",
                separator = "\n",
                postfix = "\n```",
            ) {
                "${it.fbId.id}\t${it.name}"
            }
        } else {
            onEmpty() + "\n"
        }

    private fun GlobalChatInputCommand.toHandler(
        handle: suspend GuildChatInputCommandInteractionCreateEvent.(DeferredPublicMessageInteractionResponseBehavior) -> String,
    ) {
        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            if (this@toHandler.id == interaction.invokedCommandId) {
                val response = interaction.deferPublicResponse()
                handle(response)
                    .let { response.respond { content = it } }
            }
        }
    }
}
