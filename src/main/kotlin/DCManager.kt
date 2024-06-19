package cz.lastaapps

import cz.lastaapps.model.AppConfig
import cz.lastaapps.model.Event
import cz.lastaapps.model.Post
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class DCManager private constructor(
    private val config: AppConfig,
    private val kord: Kord,
) {

    suspend fun lastPostedAt(): Instant =
        kord.rest.channel.getMessages(Snowflake(config.dcChannelID), limit = 20)
            .firstOrNull {
                it.author.bot.asNullable == true
            }?.timestamp ?: Instant.DISTANT_PAST

    suspend fun sendPost(post: Post, event: Event?) {
        val original = kord.rest.channel.createMessage(Snowflake(config.dcChannelID)) {
            embed {
                timestamp = post.publishedAt
                author {
                    name = "AUTHOR NAME"
                }
                title = post.author
                val reference = post.references?.let { "\n\n**${it.author}**\n${it.description}" } ?: ""
                description = (post.description + reference).trimToDescription()
                url = post.postLink()
                image = post.images.firstOrNull()
                color = Color(244, 186, 212)
                field {
                    this.name = "FILED_NAME"
                    this.value = "FILED_VALUE"
                    this.inline = false
                }
                field {
                    this.name = "FILED_NAME_INLINE"
                    this.value = "FILED_VALUE_INLINE"
                    this.inline = true
                }
            }
        }

        if (event == null) { return }

        kord.rest.channel.createMessage(Snowflake(config.dcChannelID)) {
            messageReference = original.id
            embed {
                timestamp = post.publishedAt
                title = event.title
                description = event.description.trimToDescription()
                url = event.eventLink()
                image = event.img
                color = Color(244, 186, 212)
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
        suspend fun create(config: AppConfig): DCManager {
            val kord = Kord(config.dcToken)
            coroutineScope { launch { kord.login() } }
            println("HERE!!!!!!!!!!")
            return DCManager(config, kord)
        }
    }
}
