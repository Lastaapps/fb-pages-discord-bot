package cz.lastaapps.api.data

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.model.Event
import cz.lastaapps.api.data.model.PagePost
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.DCMessageID
import cz.lastaapps.api.domain.model.id.toSnowflake
import cz.lastaapps.common.colorsSet
import cz.lastaapps.common.imageExtensions
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.embed
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import kotlin.math.absoluteValue

class DiscordAPI(
    private val client: HttpClient,
    private val discordKord: DiscordKord,
) {
    private val log = Logger.withTag("DiscordAPI")

    private val kord get() = discordKord.kord
    private val rest get() = kord.rest

    private suspend fun UserMessageCreateBuilder.addFile(
        nameWithoutExtension: String,
        url: String,
        client: HttpClient,
    ): NamedFile? {
        val extension =
            imageExtensions.firstOrNull { url.contains(it) } ?: run {
                log.e { "Url ($url) does not contain any of the known extensions!" }
                return null
            }
        val response = client.get(url).bodyAsChannel()
        return addFile(nameWithoutExtension + extension, ChannelProvider(null) { response })
    }

    suspend fun postPostAndEvents(
        channelID: DCChannelID,
        page: AuthorizedPage,
        post: PagePost,
        events: List<Event>,
    ): DCMessageID {
        log.d { "Posting post ${post.id} and events ${events.map { it.id }} from page ${page.fbId.id} to channel ${channelID.id}" }

        val postColor = colorsSet[(page.name.hashCode() % colorsSet.size).absoluteValue]

        val message =
            kord.rest.channel.createMessage(channelID.toSnowflake()) {
                val postImageUrl =
                    post
                        .images()
                        .firstOrNull()
                        ?.let { url ->
                            addFile("post_img", url, client)
                        }?.url

                val postDescription =
                    buildString {
                        post.titlesAndDescriptions().forEach { (title, body) ->
                            title?.let { append("**$it**\n") }
                            body?.let { append("$it\n") }
                            append('\n')
                            append('\n')
                        }
                    }.trimToDescription()

                if (postDescription.isNotBlank() || postImageUrl != null) {
                    embed {
                        timestamp = post.createdAt
                        title = page.name
                        description = postDescription
                        url = post.toURL()
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
                                        it.toURL(),
                                    ).joinToString("\n")
                            }
                        }

                        if (post.images().size > 1) {
                            field {
                                name = "Album"
                                value = "Tento příspěvek skrývá více fotek/videí"
                            }
                        }
                        post.links().takeIf { it.isNotEmpty() }?.let {
                            field {
                                name = "Odkazy"
                                value = it.joinToString("\n")
                            }
                        }
                    }
                }

                events.forEach { event ->
                    val eventImageUrl =
                        event.coverPhoto
                            ?.source
                            ?.let { url ->
                                addFile("event_img", url, client)
                            }?.url

                    embed {
                        timestamp = post.createdAt
                        title = event.name
                        description = event.description?.trimToDescription()
                        url = event.toURL()
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
            }
        return DCMessageID(message.id.value)
    }

    // description has a limit of 4096 characters
    private fun String.trimToDescription() =
        if (length > 4000) {
            val ind = substring(0, 4000).lastIndexOf(' ')
            substring(0, ind) + "..."
        } else {
            this
        }
}
