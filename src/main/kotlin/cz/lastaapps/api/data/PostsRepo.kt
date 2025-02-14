package cz.lastaapps.api.data

import arrow.core.Either
import arrow.core.None
import arrow.core.filterOption
import arrow.core.raise.either
import arrow.core.some
import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.error.DomainError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.DiscordChannel
import cz.lastaapps.api.domain.model.id.DBChannelID
import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.DCMessageID
import cz.lastaapps.api.domain.model.id.FBEventID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBPostID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.toPageAccessToken
import cz.lastaapps.api.presentation.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class PostsRepo(
    private val config: AppConfig,
    private val database: AppDatabase,
    private val appTokenProvider: AppTokenProvider,
    private val dataApi: FBDataAPI,
    private val discordApi: DiscordAPI,
) {
    private val log = Logger.withTag("PostsRepo")
    private inline val curd get() = database.database.postedPostQueries
    private inline val queries get() = database.database.queriesQueries

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var processBatchJob: Job? = null
    private var scheduleJob: Job? = null

    suspend fun requestNow() = mutex.withLock {
        if (processBatchJob?.isActive == true) {
            log.i { "Batch job is already running, skipping" }
            return
        }

        log.i { "Starting a new post posts job" }

        scheduleJob?.cancel()
        scheduleJob = scope.launch {
            while (true) {
                mutex.withLock {
                    processBatchJob = scope.launch {
                        Either.runCatching {
                            processBatch().onLeft {
                                log.e(it) { "Failed to fetch and post new posts" }
                            }
                        }.onFailure { log.e(it) { "Failed to fetch and post new posts (CRITICAL)" } }
                    }
                }
                val nextRun = Clock.System.now().plus(config.interval).toLocalDateTime(TimeZone.UTC)
                log.i { "Waiting for ${config.interval} (next run at $nextRun)" }
                delay(config.interval)
            }
        }
    }

    private suspend fun processBatch() = either<DomainError, Unit> {
        log.i { "Starting badge processing..." }

        val badge = loadPageDiscordPairs().bind()
        val pageToPosts = badge.pages.entries.parMap(concurrency = 5) { (pageID, page) ->
            log.d { "Fetching page ${page.name}" }
            dataApi.loadPagePosts(page.fbId, page.accessToken).map { posts ->
                posts.filter { it.canBePublished() }
                    .map { pageID to (it to page) }
            }
        }.map { it.bind() }
            .flatten()
            .toMap()

        val postsMap = pageToPosts.values.associateBy { FBPostID(it.first.id) }

        badge.requests.entries.parMap(concurrency = 1) { (channel, pages) ->
            log.d { "Processing channel ${channel.name} (${channel.dbId.id})" }
            val posts = pages.mapNotNull { pageToPosts[it.fbId] }
            val postIds = posts.map { FBPostID(it.first.id) }.toSet()
            val existingPosts = curd.getPostedPostsByIds(channel.dbId, postIds)
                .executeAsList()
                .toSet()
            val newPosts = postIds - existingPosts
            log.d { "Found ${newPosts.size} new posts" }

            newPosts
                .sortedBy { postsMap[it]!!.first.createdAt }
                .forEach {
                    val (post, page) = postsMap[it]!!
                    val events = post.accessibleEventIDs().parMap(concurrency = 1) { id ->
                        dataApi.loadEventData(FBEventID(id), page.accessToken).bind()
                    }.filter { event -> event.canBePublished() }
                    log.i {
                        "Posting ${post.id} (${
                            post.message?.take(24)?.replace("\n", "\\n")?.plus("...")
                        }) to ${channel.name} (${channel.name})"
                    }
                    val messageID = discordApi.postPostAndEvents(
                        channel.dcId, page, post, events,
                    ).bind()
                    createMessagePostRelation(channel.dbId, page.dbId, FBPostID(post.id), messageID)
                }
        }
    }

    private data class BatchParam(
        val requests: Map<DiscordChannel, List<AuthorizedPage>>,
        val channels: Collection<DiscordChannel>,
        val pages: Map<FBPageID, AuthorizedPage>,
    )

    /**
     * Return discord channel ID and page access token of the pages related to the channel
     */
    private suspend fun loadPageDiscordPairs(): Outcome<BatchParam> = either {
        val hasPublic = config.facebook.enabledPublicContent
        val appToken = if (hasPublic) {
            appTokenProvider.provide().bind().toPageAccessToken()
        } else {
            null
        }

        queries.getPagesAndChannelsWithTokens {
                id: DBChannelID,
                chName: String,
                dcId: DCChannelID,
                dbId: DBPageID,
                fbId: FBPageID,
                name: String,
                token: PageAccessToken?,
            ->
            val pageToken = token ?: appToken ?: run {
                // TODO notify user somehow, probably IOR
                log.e { "Page $name is requested by $chName (${id.id}), but it's not authorized" }
                return@getPagesAndChannelsWithTokens None
            }

            (DiscordChannel(id, chName, dcId) to AuthorizedPage(
                dbId = dbId,
                fbId = fbId,
                name = name,
                accessToken = pageToken,
            )).some()
        }
            .executeAsList()
            .filterOption()
            .groupBy { it.first }
            .mapValues { (_, value) -> value.map { it.second } }
            .let { channelsToPages ->
                val pages = channelsToPages.values
                    .flatten()
                    .associateBy { it.fbId }

                BatchParam(
                    requests = channelsToPages,
                    channels = channelsToPages.keys,
                    pages = pages,
                )
            }
    }

    private fun createMessagePostRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
        postID: FBPostID,
        messageID: DCMessageID,
    ) {
        log.d { "Creating relation between message ${messageID.id} and post ${postID.id}" }
        curd.createPostedPost(null, channelID, pageID, postID, messageID)
    }
}
