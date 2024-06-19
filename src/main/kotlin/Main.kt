package cz.lastaapps

import arrow.core.Either
import cz.lastaapps.model.AppConfig
import cz.lastaapps.model.AppCookies
import cz.lastaapps.parser.FacebookEventParser
import cz.lastaapps.parser.FacebookFeedParser
import cz.lastaapps.parser.FacebookPostParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes


fun main(): Unit = runBlocking {
    val config = loadConfig()
    val client = createClient(config.cookies)
//    val dcApi= DCManager.create(config)
    val clock = Clock.System

    suspend fun randomDelay() {
        delay(Random.nextInt(100..2000).milliseconds)
    }

    while (true) {
        val start = clock.now()
//        val lastPosted = dcApi.lastPostedAt()
        val lastPosted = Instant.DISTANT_PAST

        config.pageIds.map { pageId ->
            randomDelay()
            println("Fetching page $pageId")

            val body = downloadFeed(client, pageId)
            FacebookFeedParser.parsePostsFromFeed(body)
                .also { println("Got ${it.size} posts") }
        }.flatten()
            .sortedByDescending { it.publishedAt }
            .filter { lastPosted < it.publishedAt }
            .also { println("Found ${it.size} new posts") }
            .also { println("Fetching posts") }
            .onEach(::println)
            .mapNotNull { post ->
                randomDelay()
                println("Fetching the post from '${post.author}' starting '${post.description.take(20)}...'")

                // in case there is a post with only an event, the does not exist a post page - nothing to parse
                if (post.eventId!=null && !post.id.startsWith("pfbid")) {
                    return@mapNotNull post
                }
                Either.catch {
                    val body = downloadPost(client, pageId = post.pageId, postId = post.id)
                    FacebookPostParser.parsePost(body, pageId = post.pageId, postId = post.id)
                }.onLeft { it.printStackTrace() }.getOrNull()
            }
            .also { println("Fetching events") }
            .map { post ->
                post to post.eventId?.let { eventId ->
                    randomDelay()
                    println("Fetching event '$eventId' for the post from '${post.author}' starting '${post.description.take(20)}...'")

                    Either.catch {
                        val body = downloadEvent(client, eventId = eventId)
                        FacebookEventParser.parseEvent(body, eventId)
                    }.onLeft { it.printStackTrace() }.getOrNull()
                }
            }
            .also { println("Posting to Discord") }
            .forEach {  (post, event) ->
                println(post)
                event?.let { println(it) }
//                dcApi.sendPost(post, event)
            }

        val wait = config.delay - (clock.now() - start)
        println("Done, waiting for ${wait.inWholeMinutes} minutes")
        delay(wait)
    }
}

fun loadConfig(): AppConfig = AppConfig(
    AppCookies(
        cUser = System.getenv("FACEBOOK_COOKIE_c_user"),
        xs = System.getenv("FACEBOOK_COOKIE_x_s"),
        mPageVoice = System.getenv("FACEBOOK_COOKIE_m__page_voice"),
    ),
    dcToken = System.getenv("FACEBOOK_DC_TOKEN"),
    dcChannelID = System.getenv("FACEBOOK_DC_CHANNEL"),
    pageIds = System.getenv("FACEBOOK_PAGES").split(","),
    delay = System.getenv("FACEBOOK_DELAY_MINS").toInt().minutes,
)
