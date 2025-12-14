package cz.lastaapps.api.data.api

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.util.formatDateTime
import cz.lastaapps.api.domain.AppDCPermissionSet
import cz.lastaapps.api.domain.error.LogicError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.error.catchingDiscord
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.Event
import cz.lastaapps.api.domain.model.Post
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.DCMessageID
import cz.lastaapps.api.domain.model.id.toSnowflake
import cz.lastaapps.common.colorsSet
import cz.lastaapps.common.imageExtensions
import dev.kord.common.entity.Permissions
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.embed
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.fullPath
import kotlin.math.absoluteValue

class DiscordAPI(
    private val client: HttpClient,
    private val discordKord: DiscordKord,
) {
    private val log = Logger.withTag("DiscordAPI")

    private val kord get() = discordKord.kord
    private suspend fun UserMessageCreateBuilder.addFile(
        nameWithoutExtension: String,
        url: Url,
        client: HttpClient,
    ): Outcome<NamedFile?> = catchingDiscord {
        val extension =
            imageExtensions.firstOrNull { url.fullPath.lowercase().contains(it) } ?: run {
                log.e { "Url ($url) does not contain any of the known extensions!" }
                return@catchingDiscord null
            }
        val response = client.get(url).bodyAsChannel()
        addFile(nameWithoutExtension + extension, ChannelProvider(null) { response })
    }

    suspend fun postPostAndEvents(
        channelID: DCChannelID,
        page: AuthorizedPage,
        post: Post,
        events: List<Event>,
    ): Outcome<DCMessageID> = catchingDiscord {
        log.d { "Posting post ${post.id.id} and events ${events.map { it.id }} from page ${page.fbId.id} to channel ${channelID.id}" }

        val postColor = colorsSet[(page.name.hashCode() % colorsSet.size).absoluteValue]

        val message =
            kord.rest.channel.createMessage(channelID.toSnowflake()) {
                var anyEmbedPosted = false
                val postImageUrl =
                    post
                        .images
                        .firstOrNull()
                        ?.let { url ->
                            addFile("post_img", url, client).bind()
                        }?.url

                val postDescription =
                    buildString {
                        post.sections.forEach { section ->
                            section.title?.let { append("**$it**\n") }
                            section.message?.let { append("$it\n") }
                            append('\n')
                            append('\n')
                        }
                    }.trimToDescription()

                if (postDescription.isNotBlank() || postImageUrl != null) {
                    anyEmbedPosted = true
                    embed {
                        timestamp = post.createdAt
                        title = page.name
                        // post's description cannot be empty.
                        // It may be empty for posts with only a single photo
                        description = postDescription.takeUnless { it.isBlank() } ?: "\uD83D\uDCF7"
                        url = post.link.link.toString()
                        image = postImageUrl
                        color = postColor

                        post.place?.let {
                            field {
                                name = it.name ?: it.location?.city ?: "Neznámo kde"
                                value =
                                    listOfNotNull(
                                        listOfNotNull(
                                            it.location?.city,
                                            it.location?.formattedLatitude,
                                            it.location?.formattedLongitude,
                                        ).joinToString(", ").takeIf { it.isNotBlank() },
                                        it.link,
                                    ).joinToString("\n")
                            }
                        }

                        if (post.images.size > 1) {
                            field {
                                name = "Album"
                                value = "Tento příspěvek skrývá více fotek/videí"
                            }
                        }
                        post.linksInAttachments.takeIf { it.isNotEmpty() }?.let {
                            field {
                                name = "Odkazy"
                                value = it.joinToString("\n")
                            }
                        }
                    }
                }

                events.forEach { event ->
                    log.i { "Posting event (accessible) ${event.link}" }
                    anyEmbedPosted = true
                    val eventImageUrl =
                        event.coverPhotoLink
                            ?.let { url ->
                                addFile("event_img", url, client).bind()
                            }?.url

                    embed {
                        timestamp = post.createdAt
                        title = event.name
                        description = event.description?.trimToDescription()
                        url = event.link.toString()
                        image = eventImageUrl
                        color = postColor

                        // event time
                        listOfNotNull(event.startAt, event.endAt)
                            .takeUnless { it.isEmpty() }
                            ?.joinToString(" — ") { it.formatDateTime(event.timezone) }
                            ?.let {
                                field {
                                    name = "Kdy?"
                                    value = it
                                }
                            }

                        event.place?.name?.let {
                            field {
                                name = "Kde?"
                                value = it
                            }
                        }
                        if (event.isOnline) {
                            field {
                                name = "Onlive událost"
                                value = "Více info v příspěvku"
                            }
                        }
                    }
                }

                // If the post references another post or event that we don't have access to
                // both the message and images can be empty. In that case the code would send
                // an empty message, which is prohibited by DC API.
                // https://facecook.com/1446471142347491_1183196140476661
                if (!anyEmbedPosted) {
                    embed {
                        timestamp = post.createdAt
                        title = page.name
                        url = post.link.link.toString()
                        color = postColor
                        description = "This post cannot be sadly process by the bot.\n" + post.link.link.toString()
                    }
                }
            }


        // Creates previews for links contained in the post's text
        // that were not present in attachments (we cannot access them using API)
        if (false) ((post.inaccessibleEventIds.map { eventId -> Url("https://www.facebook.com/events/${eventId.id}") } +
            post.linksInText.map { it.link })
            .forEach { link ->
                log.i { "Posting event (inaccessible) $link" }
                kord.rest.channel.createMessage(channelID.toSnowflake()) {
                    content = link.toString()
                    messageReference = message.id
                }
            })

        // If the channel is an announcement channel and some other servers follow it,
        // this will also send the message to the other servers
        // TODO This has to be made opt in per channel as it causes rate limiting in case many posts are posted at once
//        Either.catch {
//            kord.rest.channel.crossPost(channelID.toSnowflake(), message.id)
//        }.onLeft {
//            log.e { "Failed to cross post the message (is it an announcement channel)" }
//        }

        DCMessageID(message.id.value)
    }

    suspend fun sendSimpleMessage(
        channelID: DCChannelID,
        message: String,
        allowEmbeds: Boolean = true,
    ) = catchingDiscord {
        kord.rest.channel.createMessage(channelID.toSnowflake()) {
            content = message
            suppressEmbeds = !allowEmbeds
        }
    }.map {}

    // the description has a limit of 4096 characters
    private fun String.trimToDescription() =
        if (length > 4000) {
            val ind = substring(0, 4000).lastIndexOf(' ')
            substring(0, ind) + "..."
        } else {
            this
        }

    suspend fun getServerNameForChannel(channelID: DCChannelID): Outcome<String> = catchingDiscord {
        val channel = kord.rest.channel.getChannel(channelID.toSnowflake())
        val serverId = channel.guildId.value ?: raise(LogicError.CannotAccessServerName)
        val server = kord.rest.guild.getGuild(serverId)
        server.name
    }

    suspend fun checkBotPermissions(
        channelID: DCChannelID,
        requiredPermissions: Collection<AppDCPermissionSet>,
    ): Outcome<Map<AppDCPermissionSet, Boolean>> = catchingDiscord {
        val botId = kord.selfId

        val channel = kord.rest.channel.getChannel(channelID.toSnowflake())
        val guildId = channel.guildId.value
        if (guildId == null) {
            log.e { "Channel ${channelID.id} guild info cannot be accessed" }
            return@catchingDiscord requiredPermissions.associateWith { false }
        }

        val member = kord.rest.guild.getGuildMember(guildId, botId)
        val roles = kord.rest.guild.getGuildRoles(guildId)

        var guildPermissions = Permissions()
        member.roles.forEach { roleId ->
            roles.find { it.id == roleId }?.let { role ->
                guildPermissions += role.permissions
            }
        }

        var channelPermissionsAllowed = Permissions()
        var channelPermissionsDenied = Permissions()
        channel.permissionOverwrites.value?.forEach { overwrite ->
            if (overwrite.id == botId || member.roles.contains(overwrite.id)) {
                channelPermissionsAllowed += overwrite.allow
                channelPermissionsDenied += overwrite.deny
            }
        }

        val permissions = guildPermissions + channelPermissionsAllowed - channelPermissionsDenied
        log.d { "Channel ${channelID.id} has following permissions: ${permissions.prettyPrint()}" }
        requiredPermissions.associateWith { permissions.contains(it.permissions) }
    }
}

private fun Permissions.prettyPrint() = values.map { it::class.simpleName }
