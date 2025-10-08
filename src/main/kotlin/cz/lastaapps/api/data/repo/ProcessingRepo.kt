package cz.lastaapps.api.data.repo

import arrow.core.Either
import arrow.core.None
import arrow.core.filterOption
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.some
import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.AppDatabase
import cz.lastaapps.api.data.api.DiscordAPI
import cz.lastaapps.api.data.provider.EventProvider
import cz.lastaapps.api.data.provider.PostProvider
import cz.lastaapps.api.domain.AppDCPermissions
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.error.DomainError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.error.text
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.DiscordChannel
import cz.lastaapps.api.domain.model.id.DBChannelID
import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.DCMessageID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBPostID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.toPageAccessToken
import cz.lastaapps.api.presentation.AppConfig
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Yes, this is business logic and it should not be in data layer
// I may fix it later at some point, will see
class ProcessingRepo(
    private val config: AppConfig,
    private val database: AppDatabase,
    private val appTokenProvider: AppTokenProvider,
    private val postProvider: PostProvider,
    private val eventProvider: EventProvider,
    private val discordApi: DiscordAPI,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("ProcessingRepo")
    private inline val curd get() = database.database.postedPostQueries
    private inline val queries get() = database.database.queriesQueries

    private val mutex = Mutex()
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
                log.i {
                    val nextRun = Clock.System.now()
                        .epochSeconds.let(Instant::fromEpochSeconds)
                        .plus(config.interval).toLocalDateTime(TimeZone.UTC)

                    "Next run is scheduled for $nextRun (in ${config.interval})"
                }

                mutex.withLock {
                    processBatchJob = scope.launch {
                        Either.runCatching {
                            processBatch()
                                .onLeft { log.e(it) { "Failed to fetch and post new posts" } }
                                .onRight { log.i { "Batch processed, see you soon" } }
                        }.onFailure { log.e(it) { "Failed to fetch and post new posts (CRITICAL)" } }
                    }
                }
                delay(config.interval)
            }
        }
    }

    private suspend fun processBatch() = either<DomainError, Unit> {
        log.i { "Starting badge processing..." }

        val batch = loadPageDiscordPairs().bind()

        val pageIdToPage = batch.pages.values.associateBy { it.fbId }
        val pageIdToPosts = batch.pages.entries
            .parMap(concurrency = config.concurrency.fetchPages) { (pageID, page) ->
                log.d { "Fetching page ${page.name}" }
                postProvider.loadPagePosts(page.fbId, page.accessToken, limit = config.facebook.fetchPostsLimit)
                    .map { posts ->
                    posts.let { pageID to it }
                }
            }.associate { it.bind() }

        val postsMap = pageIdToPosts.values.flatten().associateBy { it.id }
        val postIdToPageId = pageIdToPosts.entries.map { (page, posts) ->
            posts.map { it.id to page }
        }.flatten().toMap()

        batch.requests.entries
            .filter { (channel, _) -> channel.enabled }
            .filter { (channel, _) ->
                discordApi.checkBotPermissions(channel.dcId, AppDCPermissions.forPosting)
                    .onLeft { log.i { "Channel (${channel.name} - ${channel.dcId.id}) cannot be processed for permissions - ${it.text()}" } }
                    .onRight {
                        if (!it) {
                            log.i { "Channel (${channel.name} - ${channel.dcId.id}) does not have sufficient permissions for posting" }
                        }
                    }
                    .getOrElse { false }
            }
            .parMap(concurrency = config.concurrency.postPosts) { (channel, pages) ->
                either {
                    log.d { "Processing channel ${channel.name} (${channel.dbId.id})" }
                    val posts = pages.mapNotNull { pageIdToPosts[it.fbId] }.flatten()
                    val postIds = posts.map { it.id }.toSet()
                    val existingPosts = curd.getPostedPostsByIds(channel.dbId, postIds)
                        .executeAsList()
                        .toSet()

                    val newPosts = (postIds - existingPosts)
                        .also { log.d { "Found ${it.size} new posts for channel ${channel.name} (${channel.dbId.id})" } }
                        .parMap(concurrency = config.concurrency.resolvePosts) { postsMap[it]!!().bind() }

                    newPosts
                        .sortedBy { it.createdAt }
                        .forEach { post ->
                            val page = pageIdToPage[postIdToPageId[post.id]!!]!!

                            val events = post.accessibleEventIds.parMap(concurrency = 1) { id ->
                                eventProvider.loadEventData(id, page.accessToken)().bind()
                            }.filterOption()
                            log.i {
                                "Posting ${post.id.id} to ${channel.name} (${channel.name}) -> \"${
                                    post.message?.take(24)?.replace("\n", "\\n")?.plus("...")
                                }\""
                            }
                            val messageID = discordApi.postPostAndEvents(
                                channel.dcId, page, post, events,
                            ).bind()
                            createMessagePostRelation(channel.dbId, page.dbId, post.id, messageID)
                        }
                } to channel
            }.onEach { (it, channel) ->
                it.onLeft { log.e(it) { "Failed to process channel ${channel.name} (${channel.dcId.id})" } }
            }.forEach { it.first.bind() }
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
                channelEnabled: Boolean,
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

            (DiscordChannel(id, chName, dcId, channelEnabled) to AuthorizedPage(
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
