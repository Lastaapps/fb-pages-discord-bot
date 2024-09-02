package cz.lastaapps.scraping

import arrow.core.Either
import cz.lastaapps.scraping.model.AppConfig
import cz.lastaapps.scraping.model.AppCookies
import cz.lastaapps.scraping.parser.FacebookEventParser
import cz.lastaapps.scraping.parser.FacebookFeedParser
import cz.lastaapps.scraping.parser.FacebookPostParser
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun main(): Unit =
    runBlocking {
        println("Starting...")
        println("Loading config...")
        val config = loadConfig()
        val client = createClient(config.cookies)
        val dcApi = DCManager.create(config, client)
        val clock = Clock.System

        println("Connecting to DC...")
        launch { dcApi.login() }

        println("----------------------------------------------------------------")
        println("Started")
        println("Debug mode: ${config.debugMode}")
        println("Delay: ${config.delay}")
        println("ChannelID: ${config.dcChannelID}")
        println("Facebook pages: ${config.pageIds.joinToString(", ")}")
        println("----------------------------------------------------------------")

        suspend fun randomDelay() {
            if (config.debugMode) {
                delay(Random.nextInt(100..2000).milliseconds)
            } else {
                delay(Random.nextInt(1000..5000).milliseconds)
            }
        }

        // in case the app starts crashing and restarting,
        // I don't want the bot to make to many requests to not get banned
        if (!config.debugMode) {
            val toWait =
                config.delay.inWholeMinutes.toInt().let {
                    Random.nextInt(min(5, it)..it)
                }.minutes
            println("Initial starting delay $toWait...")
            delay(toWait)
        }

        while (true) {
            println("Here we go again!")

            val start = clock.now()
            val lastPosted = dcApi.lastPostedAt()
            println("Last posted message: $lastPosted")

            config.pageIds.map { pageId ->
                randomDelay()
                println("Fetching page $pageId")

                val body = downloadFeed(client, pageId)
                FacebookFeedParser.parsePostsFromFeed(body, config.timeZonePost)
                    .also { println("Got ${it.size} posts") }
            }.flatten()
                .sortedBy { it.publishedAt }
                .filter { lastPosted < it.publishedAt }
                .also { println("Found ${it.size} new posts") }
                .also { println("Fetching posts") }
                .onEach(::println)
                .mapNotNull { post ->
                    randomDelay()
                    println("Fetching the post from '${post.author}' starting '${post.description.take(32)}...'")

                    // in case there is a post with only an event, profile/cover photo update, ...
                    // generally post without post detail, there is nothing more to parse nothing to parse
                    if (!post.id.startsWith("pfbid")) {
                        return@mapNotNull post
                    }
                    Either.catch {
                        val body = downloadPost(client, pageId = post.pageId, postId = post.id)
                        FacebookPostParser.parsePost(
                            body,
                            pageId = post.pageId,
                            postId = post.id,
                            config.timeZonePost,
                        ).let {
                            if (post.publishedAt != it.publishedAt) {
                                // When can this happen?
                                // Just now -> minutes
                                // minutes -> hours
                                // hours -> Yesterday
                                println("---------- !!! WARNING !!! ----------")
                                println("The time in both scrapes differ: feed: ${post.publishedAt} x ${it.publishedAt}")
                                println("-------------------------------------")
                                it.copy(publishedAt = post.publishedAt)
                            } else {
                                it
                            }
                        }
                    }.onLeft { it.printStackTrace() }.getOrNull()
                }
                .also { println("Fetching events") }
                .map { post ->
                    post to
                        post.eventId?.let { eventId ->
                            randomDelay()
                            println(
                                "Fetching event '$eventId' for " +
                                    "the post from '${post.author}' starting '${post.description.take(32)}...'",
                            )

                            Either.catch {
                                val body = downloadEvent(client, eventId = eventId)
                                FacebookEventParser.parseEvent(body, eventId)
                            }.onLeft { it.printStackTrace() }.getOrNull()
                        }
                }
                .also { println("Posting to Discord") }
                .forEach { (post, event) ->
                    println("Sending post:")
                    println("--------------------------------")
                    println(post)
                    println("--------------------------------")

                    event?.let {
                        println("Sending event:")
                        println("--------------------------------")
                        println(it)
                        println("--------------------------------")
                    }
                    dcApi.sendPost(post, event)
                }

            while (clock.now().toLocalDateTime(config.activeHoursTimezone).hour !in config.activeHoursRange) {
                println("Waiting for active hours...")
                delay(1.hours)
            }

            val wait = config.delay - (clock.now() - start) + Random.nextInt(-5..5).minutes
            println("Done, waiting for ${wait.inWholeMinutes} minutes")
            delay(wait)
        }
    }

fun loadConfig(): AppConfig =
    AppConfig(
        debugMode = System.getenv("FACEBOOK_DEBUG").toBoolean(),
        AppCookies(
            cUser = System.getenv("FACEBOOK_COOKIE_c_user"),
            xs = System.getenv("FACEBOOK_COOKIE_x_s"),
            mPageVoice = System.getenv("FACEBOOK_COOKIE_m__page_voice"),
        ),
        dcToken = System.getenv("FACEBOOK_DC_TOKEN"),
        dcChannelID = System.getenv("FACEBOOK_DC_CHANNEL"),
        pageIds = System.getenv("FACEBOOK_PAGES").split(","),
        delay = System.getenv("FACEBOOK_DELAY_MINS").toInt().minutes,
        timeZoneFeed = System.getenv("FACEBOOK_TIMEZONE_FEED").let { TimeZone.of(it) },
        timeZonePost = System.getenv("FACEBOOK_TIMEZONE_POST").let { TimeZone.of(it) },
        activeHoursRange = System.getenv("FACEBOOK_ACTIVE_HOURS_START").toInt()..<System.getenv("FACEBOOK_ACTIVE_HOURS_END").toInt(),
        activeHoursTimezone = System.getenv("FACEBOOK_TIMEZONE_ACTIVE_HOURS").let { TimeZone.of(it) },
    )
