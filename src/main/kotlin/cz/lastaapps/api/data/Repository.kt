package cz.lastaapps.api.data

import api.DC_Channel
import api.DC_Channel_FB_Page
import api.FB_Page
import api.FB_Page_FB_Page_Token
import api.FB_Page_Token
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arrow.core.Option
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toOption
import co.touchlab.kermit.Logger
import cz.lastaapps.api.Database
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.error.DomainError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.AuthorizedPageFromUser
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.id.DBChannelID
import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.DBPageTokenID
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.toSnowflake
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.toPageAccessToken
import cz.lastaapps.api.presentation.AppConfig

class Repository(
    private val config: AppConfig,
    private val discordKord: DiscordKord,
    private val appTokenProvider: AppTokenProvider,
    private val authApi: FBAuthAPI,
    private val dataApi: FBDataAPI,
) {
    private val log = Logger.withTag("Store")

    private val database: Database = createDatabase(createDriver(config.databaseFileName))
    private val curd get() = database.schemaQueries
    private val queries get() = database.queriesQueries

    private val kord get() = discordKord.kord

    suspend fun getDiscordChannelID(channelID: DCChannelID): Outcome<DBChannelID> = run {
        curd.getByDCID(channelID).executeAsOneOrNull()
            ?.let { return it.right() }

        val name = kord.rest.channel.getChannel(channelID.toSnowflake()).name.value!!

        curd.transactionWithResult {
            curd.createDCChannel(null, name = name, dc_id = channelID)
            curd.lastDCChannelID().executeAsOne()
        }
    }.right()

    /**
     * Gets MyPageID for the page given by facebook page ID.
     * If the page is already authorized, returns the page.
     * If the page is not authorized, but the app has Public Content feature,
     * page info is fetched, stored and id is returned.
     * If the page id is unauthorized nor Public Content feature is available,
     * an error is returned.
     */
    suspend fun getFBPageID(
        fbPageID: FBPageID,
        allowUnauthorized: Boolean? = null,
        allowFetch: Boolean = true,
    ): Outcome<DBPageID> = either {
        @Suppress("NAME_SHADOWING")
        val allowUnauthorized = allowUnauthorized ?: config.facebook.enabledPublicContent

        // if the page reference exists, get it
        if (allowUnauthorized) {
            curd.getPageByFBId(fbPageID).executeAsOneOrNull()
        } else {
            queries.getAuthorizedPageByFBId(fbPageID).executeAsOneOrNull()
        }?.let { return@either it }

        if (!allowUnauthorized || !allowFetch) {
            return DomainError.PageNotAuthorized.left()
        }

        val appToken = appTokenProvider.provide()
        val metadata = authApi.getPageMetadata(pageID = fbPageID, pageAccessToken = appToken.toPageAccessToken()).bind()
        curd.transactionWithResult {
            curd.createFBPage(null, name = metadata.name, FBPageID(metadata.fbId.toULong()))
            curd.lastFBPageID().executeAsOne()
        }
    }

    fun storeAuthorizedPage(authorizedPage: AuthorizedPageFromUser) {
        log.d {
            "Storing authorized page ${authorizedPage.pageID.id} ${authorizedPage.pageName} " +
                "from user ${authorizedPage.userID.id} ${authorizedPage.userName}"
        }
        with(authorizedPage) {
            curd.transactionWithResult {
                curd.createFBPage(null, pageName, pageID)
                val pageId = curd.lastFBPageID().executeAsOne()
                curd.createFBPageToken(null, pageAccessToken, granted_by = userName)
                val tokenID = curd.lastFBPageTokenID().executeAsOne()
                curd.linkFBPageToFBPageToken(null, fb_page_id = pageId, fb_page_token_id = tokenID)
            }
        }
    }

    fun getPageByID(id: DBPageID): Outcome<Option<Page>> = run {
        curd.getPageByID(id).executeAsOneOrNull()
            ?.let { Page(fbId = it.fb_id, name = it.name) }
            .toOption()
            .right()
    }

    fun getPageIDByName(name: String): Outcome<Option<DBPageID>> = run {
        curd.getPageIDByName(name).executeAsOneOrNull()
            .toOption()
            .right()
    }

    suspend fun loadAuthorizedPages(): List<AuthorizedPage> =
        if (config.facebook.enabledPublicContent) {
            val appToken = appTokenProvider.provide().toPageAccessToken()
            curd
                .getAllPages()
                .executeAsList()
                .map { (dbPageID, pageName, fbPageID) ->
                    AuthorizedPage(fbId = fbPageID, name = pageName, accessToken = appToken)
                }
        } else {
            queries
                .getAuthorizedPages()
                .executeAsList()
                .map { (pageID, pageName, fbPageID, pageAccessToken) ->
                    AuthorizedPage(fbId = fbPageID, name = pageName, accessToken = pageAccessToken)
                }
        }

    suspend fun loadAuthorizedPagesForChannel(channelID: DBChannelID): List<AuthorizedPage> =
        if (config.facebook.enabledPublicContent) {
            val appToken = appTokenProvider.provide().toPageAccessToken()
            queries
                .getAllPagesForChannel(channelID)
                .executeAsList()
                .map { (dbPageID, pageName, fbPageID) ->
                    AuthorizedPage(fbId = fbPageID, name = pageName, accessToken = appToken)
                }
        } else {
            queries
                .getAuthorizedPagesForChannel(channelID)
                .executeAsList()
                .map { (pageID, pageName, fbPageID, pageAccessToken) ->
                    AuthorizedPage(fbId = fbPageID, name = pageName, accessToken = pageAccessToken)
                }
        }

    /**
     * Return discord channel ID and page access token of the pages related to the channel
     */
    fun loadPageDiscordPairs(): Map<String, List<AuthorizedPage>> =
        error("")
//        queries
//            .selectChannelsWithPages()
//            .executeAsList()
//            .map {
//                with(it) {
//                    // channel_id to AuthorizedPage(id = page_id, name = page_name, accessToken = page_access_token)
//                }
//            }.groupBy { it.first }
//            .mapValues { (_, value) -> value.map { it.second } }

    fun createChannelPageRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
    ): Outcome<Unit> = curd.transactionWithResult {
        if (curd.isLinkedDCChannelToFBPage(channelID, pageID).executeAsOneOrNull() != null) {
            return@transactionWithResult DomainError.PageAlreadyLinkedToChannel.left()
        }

        log.d { "Creating relation between channel $channelID and page $pageID" }
        curd.linkDCChannelToFBPage(null, channel_id = channelID, fb_page_id = pageID)
        Unit.right()
    }

    fun removeChannelPageRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
    ): Outcome<Unit> = run {
        log.d { "Removing relation between channel $channelID and page $pageID" }
        curd.unlinkDCChannelToFBPage(channel_id = channelID, fb_page_id = pageID)
        Unit.right()
    }

    fun createMessagePostRelation(
        channelID: String,
        messageID: String,
        postID: String,
    ) {
        log.d { "Creating relation between message $messageID and post $postID" }
        curd.assignMessageToPost(channel_id = channelID, message_id = messageID, post_id = postID)
    }

    fun getMessagePostRelations(): List<Pair<String, String>> =
        curd
            .selectMessagesWithPosts()
            .executeAsList()
            .map { it.message_id to it.post_id }

    fun getMessagesRelatedToPost(
        channelID: String,
        postID: String,
    ): List<String> =
        curd
            .selectMessagesForPost(channelID, postID)
            .executeAsList()
            .map { it.message_id }

    private fun createDatabase(driver: SqlDriver): Database = Database(
        driver,
        FB_PageAdapter = FB_Page.Adapter(
            fb_idAdapter = object : ColumnAdapter<FBPageID, Long> {
                override fun decode(databaseValue: Long): FBPageID = FBPageID(databaseValue.toULong())
                override fun encode(value: FBPageID): Long = value.id.toLong()
            },
            idAdapter = object : ColumnAdapter<DBPageID, Long> {
                override fun decode(databaseValue: Long): DBPageID = DBPageID(databaseValue.toULong())
                override fun encode(value: DBPageID): Long = value.id.toLong()
            },
        ),
        DC_ChannelAdapter = DC_Channel.Adapter(
            idAdapter = object : ColumnAdapter<DBChannelID, Long> {
                override fun decode(databaseValue: Long): DBChannelID = DBChannelID(databaseValue.toULong())
                override fun encode(value: DBChannelID): Long = value.id.toLong()
            },
            dc_idAdapter = object : ColumnAdapter<DCChannelID, Long> {
                override fun decode(databaseValue: Long): DCChannelID = DCChannelID(databaseValue.toULong())
                override fun encode(value: DCChannelID): Long = value.id.toLong()
            },
        ),
        DC_Channel_FB_PageAdapter = DC_Channel_FB_Page.Adapter(
            channel_idAdapter = object : ColumnAdapter<DBChannelID, Long> {
                override fun decode(databaseValue: Long): DBChannelID = DBChannelID(databaseValue.toULong())
                override fun encode(value: DBChannelID): Long = value.id.toLong()
            },
            fb_page_idAdapter = object : ColumnAdapter<DBPageID, Long> {
                override fun decode(databaseValue: Long): DBPageID = DBPageID(databaseValue.toULong())
                override fun encode(value: DBPageID): Long = value.id.toLong()
            },
        ),
        FB_Page_FB_Page_TokenAdapter = FB_Page_FB_Page_Token.Adapter(
            fb_page_idAdapter = object : ColumnAdapter<DBPageID, Long> {
                override fun decode(databaseValue: Long): DBPageID = DBPageID(databaseValue.toULong())
                override fun encode(value: DBPageID): Long = value.id.toLong()
            },
            fb_page_token_idAdapter = object : ColumnAdapter<DBPageTokenID, Long> {
                override fun decode(databaseValue: Long): DBPageTokenID = DBPageTokenID(databaseValue.toULong())
                override fun encode(value: DBPageTokenID): Long = value.id.toLong()
            },
        ),
        FB_Page_TokenAdapter = FB_Page_Token.Adapter(
            idAdapter = object : ColumnAdapter<DBPageTokenID, Long> {
                override fun decode(databaseValue: Long): DBPageTokenID = DBPageTokenID(databaseValue.toULong())
                override fun encode(value: DBPageTokenID): Long = value.id.toLong()
            },
            tokenAdapter = object : ColumnAdapter<PageAccessToken, String> {
                override fun decode(databaseValue: String): PageAccessToken = PageAccessToken(databaseValue)
                override fun encode(value: PageAccessToken): String = value.token
            },
        ),
    )

    private fun createDriver(dbName: String): SqlDriver {
        log.d { "Creating sql driver from the DB \"$dbName\", schema version ${Database.Schema.version}" }
        val driver: SqlDriver =
            JdbcSqliteDriver(
                "jdbc:sqlite:$dbName",
                schema = Database.Schema,
            )
        return driver
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Repository.isPostPosted(
    channelID: String,
    postID: String,
): Boolean = getMessagesRelatedToPost(channelID, postID).isNotEmpty()
