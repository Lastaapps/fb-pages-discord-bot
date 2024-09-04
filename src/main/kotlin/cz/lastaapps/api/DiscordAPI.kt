package cz.lastaapps.api

import cz.lastaapps.common.colorsSet
import cz.lastaapps.common.imageExtensions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.embed
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class DiscordAPI private constructor(
    private val client: HttpClient,
    private val kord: Kord,
) {
    private val rest get() = kord.rest

    fun start(scope: CoroutineScope) {
        // TODO stop when parent scope stops
        scope.launch {
            kord.login()
        }
    }

    suspend fun getChannelName(channelID: String): String =
        rest.channel
            .getChannel(Snowflake(channelID))
            .name.value!!

    private suspend fun UserMessageCreateBuilder.addFile(
        nameWithoutExtension: String,
        url: String,
        client: HttpClient,
    ): NamedFile? {
        val extension =
            imageExtensions.firstOrNull { url.contains(it) } ?: run {
                println("Url does not contain any of the known extensions!")
                return null
            }
        val response = client.get(url).bodyAsChannel()
        return addFile(nameWithoutExtension + extension, ChannelProvider(null) { response })
    }

    suspend fun postPostAndEvents(
        channelID: String,
        postWithEvents: Triple<AuthorizedPage, PagePost, List<Event>>,
    ) {
        val (page, post, events) = postWithEvents

        val postColor = colorsSet[(page.name.hashCode() % colorsSet.size).absoluteValue]

        kord.rest.channel.createMessage(Snowflake(channelID)) {
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
                }

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
    }

    // description has a limit of 4096 characters
    private fun String.trimToDescription() =
        if (length > 4000) {
            val ind = substring(0, 4000).lastIndexOf(' ')
            substring(0, ind) + "..."
        } else {
            this
        }

    companion object {
        suspend fun create(
            config: AppConfig,
            client: HttpClient,
        ): DiscordAPI {
            val kord = Kord(config.discord.token)
            return DiscordAPI(client, kord)
        }
    }
}
