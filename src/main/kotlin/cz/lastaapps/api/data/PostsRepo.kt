package cz.lastaapps.api.data

import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import cz.lastaapps.api.domain.AppTokenProvider
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
        if (processBatchJob?.isActive == true) return

        scheduleJob?.cancel()
        scheduleJob = scope.launch {
            while (true) {
                mutex.withLock {
                    processBatchJob = scope.launch { processBatch() }
                }
                log.i { "Waiting for ${config.interval}" }
                delay(config.interval)
            }
        }
    }

    private suspend fun processBatch() {
        log.i { "Starting badge processing..." }

        val badge = loadPageDiscordPairs()
        val pageToPosts = badge.pages.entries.parMap(concurrency = 5) { (pageID, page) ->
            log.d { "Fetching page ${page.name}" }
            pageID to dataApi.loadPagePosts(page.fbId, page.accessToken)
                .filter { it.canBePublished() }
                .map { it to page }
        }.toMap()
        val postsMap = pageToPosts.values.flatten().associateBy { FBPostID(it.first.id) }

        badge.requests.entries.parMap(concurrency = 1) { (channel, pages) ->
            log.d { "Processing channel ${channel.name} (${channel.dbId.id})" }
            val posts = pages.map { pageToPosts[it.fbId] ?: emptyList() }
                .flatten()
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
                    val events = post.eventIDs().parMap(concurrency = 1) { id ->
                        dataApi.loadEventData(FBEventID(id), page.accessToken)
                    }
                    log.i {
                        "Posting ${post.id} (${
                            post.message?.take(24)?.replace("\n", "\\n")?.plus("...")
                        }) to ${channel.name} (${channel.name})"
                    }
                    val messageID = discordApi.postPostAndEvents(
                        channel.dcId, page, post, events,
                    )
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
    private suspend fun loadPageDiscordPairs(): BatchParam = run {
        if (config.facebook.enabledPublicContent) {
            val token = appTokenProvider.provide().toPageAccessToken()
            queries.getPagesAndChannels {
                    id: DBChannelID,
                    chName: String,
                    dcId: DCChannelID,
                    dbId: DBPageID,
                    fbId: FBPageID,
                    name: String,
                ->
                DiscordChannel(id, chName, dcId) to AuthorizedPage(
                    dbId = dbId,
                    fbId = fbId,
                    name = name,
                    accessToken = token,
                )
            }
        } else {
            queries.getAuthorizedPagesAndChannels {
                    id: DBChannelID,
                    chName: String,
                    dcId: DCChannelID,
                    dbId: DBPageID,
                    fbId: FBPageID,
                    name: String,
                    token: PageAccessToken,
                ->
                DiscordChannel(id, chName, dcId) to AuthorizedPage(
                    dbId = dbId,
                    fbId = fbId,
                    name = name,
                    accessToken = token,
                )
            }
        }
            .executeAsList()
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
