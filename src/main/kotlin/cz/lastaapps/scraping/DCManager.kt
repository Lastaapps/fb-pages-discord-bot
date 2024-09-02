package cz.lastaapps.scraping

import cz.lastaapps.scraping.model.AppConfig
import cz.lastaapps.scraping.model.Event
import cz.lastaapps.scraping.model.Post
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.embed
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import kotlin.math.absoluteValue
import kotlinx.datetime.Instant

class DCManager private constructor(
    private val config: AppConfig,
    private val kord: Kord,
    private val client: HttpClient,
) {
    suspend fun lastPostedAt(): Instant =
        kord.rest.channel.getMessages(Snowflake(config.dcChannelID), limit = 20)
            .firstOrNull {
                it.author.bot.asNullable == true
            }?.embeds
            ?.firstOrNull()
            ?.timestamp
            ?.value
            ?: Instant.DISTANT_PAST

    private val colors =
        listOf(
            Color(0, 0, 0),
            Color(255, 255, 255),
            Color(255, 0, 0),
            Color(0, 255, 0),
            Color(0, 0, 255),
            Color(255, 255, 0),
            Color(255, 165, 0),
            Color(128, 0, 128),
            Color(255, 192, 203),
            Color(0, 128, 128),
            Color(128, 128, 128),
            Color(139, 69, 19),
            Color(255, 140, 0),
            Color(0, 255, 127),
            Color(0, 102, 204),
            Color(238, 130, 238),
            Color(255, 204, 0),
            Color(153, 0, 153),
            Color(204, 0, 0),
            Color(255, 99, 71),
            Color(255, 182, 193),
            Color(34, 139, 34),
            Color(102, 0, 0),
            Color(184, 115, 51),
            Color(0, 128, 128),
            Color(255, 0, 255),
            Color(128, 128, 0),
        )

    private val extensions =
        listOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp",
            ".gif",
        )

    private suspend fun UserMessageCreateBuilder.addFile(
        nameWithoutExtension: String,
        url: String,
        client: HttpClient,
    ): NamedFile? {
        val extension =
            extensions.firstOrNull { url.contains(it) } ?: run {
                println("Url does not contain any of the known extensions!")
                return null
            }
        val response = client.get(url).bodyAsChannel()
        return addFile(nameWithoutExtension + extension, ChannelProvider(null) { response })
    }

    suspend fun sendPost(
        post: Post,
        event: Event?,
    ) {
        val postColor = colors[(post.author.hashCode() % colors.size).absoluteValue]

        kord.rest.channel.createMessage(Snowflake(config.dcChannelID)) {
            val postImageUrl =
                post.images.firstOrNull()?.let { url ->
                    addFile("post_img", url, client)
                }?.url

            embed {
                timestamp = post.publishedAt
                title = post.author
                val reference = post.references?.let { "\n\n**${it.author}**\n${it.description}" } ?: ""
                description = (post.description + reference).trimToDescription()
                url = post.postLink()
                image = postImageUrl
                color = postColor
            }

            event ?: return@createMessage

            val eventImageUrl =
                event.img?.let { url ->
                    addFile("event_img", url, client)
                }?.url

            embed {
                timestamp = post.publishedAt
                title = event.title
                description = event.description.trimToDescription()
                url = event.eventLink()
                image = eventImageUrl
                color = postColor
                field {
                    name = "Kdy?"
                    value = event.dateTime
                }
                field {
                    name = "Kde?"
                    value = event.where
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

    suspend fun login() {
        kord.login()
    }

    companion object {
        suspend fun create(
            config: AppConfig,
            client: HttpClient,
        ): DCManager {
            val kord = Kord(config.dcToken)
            return DCManager(config, kord, client)
        }
    }
}
