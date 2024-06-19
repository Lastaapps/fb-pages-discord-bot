package cz.lastaapps

import cz.lastaapps.model.AppConfig
import cz.lastaapps.model.Event
import cz.lastaapps.model.Post
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.builder.message.embed
import kotlin.math.absoluteValue
import kotlinx.datetime.Instant

class DCManager private constructor(
    private val config: AppConfig,
    private val kord: Kord,
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

    suspend fun sendPost(
        post: Post,
        event: Event?,
    ) {
        val postColor = colors[(post.author.hashCode() % colors.size).absoluteValue]

        kord.rest.channel.createMessage(Snowflake(config.dcChannelID)) {
            embed {
                timestamp = post.publishedAt
                title = post.author
                val reference = post.references?.let { "\n\n**${it.author}**\n${it.description}" } ?: ""
                description = (post.description + reference).trimToDescription()
                url = post.postLink()
                image = post.images.firstOrNull()
                color = postColor
            }
        }

        if (event == null) {
            return
        }

        kord.rest.channel.createMessage(Snowflake(config.dcChannelID)) {
            embed {
                timestamp = post.publishedAt
                title = event.title
                description = event.description.trimToDescription()
                url = event.eventLink()
                image = event.img
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
        suspend fun create(config: AppConfig): DCManager {
            val kord = Kord(config.dcToken)
            return DCManager(config, kord)
        }
    }
}
