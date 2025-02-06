package cz.lastaapps.api.data

import arrow.core.Option
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toOption
import co.touchlab.kermit.Logger
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.error.DomainError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.AuthorizedPageFromUser
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.id.DBChannelID
import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.toSnowflake
import cz.lastaapps.api.domain.model.token.toPageAccessToken
import cz.lastaapps.api.presentation.AppConfig

class ManagementRepo(
    private val config: AppConfig,
    private val database: AppDatabase,
    private val discordKord: DiscordKord,
    private val appTokenProvider: AppTokenProvider,
    private val authApi: FBAuthAPI,
) {
    private val log = Logger.withTag("ManagementRepo")

    private inline val curd get() = database.database.schemaQueries
    private inline val queries get() = database.database.queriesQueries

    private inline val kord get() = discordKord.kord

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
                    AuthorizedPage(dbId = dbPageID, fbId = fbPageID, name = pageName, accessToken = appToken)
                }
        } else {
            queries
                .getAuthorizedPages()
                .executeAsList()
                .map { (dbPageID, pageName, fbPageID, pageAccessToken) ->
                    AuthorizedPage(dbId = dbPageID, fbId = fbPageID, name = pageName, accessToken = pageAccessToken)
                }
        }

    suspend fun loadAuthorizedPagesForChannel(channelID: DBChannelID): List<AuthorizedPage> =
        if (config.facebook.enabledPublicContent) {
            val appToken = appTokenProvider.provide().toPageAccessToken()
            queries
                .getPagesForChannel(channelID)
                .executeAsList()
                .map { (dbPageID, pageName, fbPageID) ->
                    AuthorizedPage(dbId = dbPageID, fbId = fbPageID, name = pageName, accessToken = appToken)
                }
        } else {
            queries
                .getAuthorizedPagesForChannel(channelID)
                .executeAsList()
                .map { (dbPageID, pageName, fbPageID, pageAccessToken) ->
                    AuthorizedPage(dbId = dbPageID, fbId = fbPageID, name = pageName, accessToken = pageAccessToken)
                }
        }

    fun createChannelPageRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
    ): Outcome<Unit> = curd.transactionWithResult {
        if (curd.isLinkedDCChannelToFBPage(channelID, pageID).executeAsOneOrNull() != null) {
            return@transactionWithResult DomainError.PageAlreadyLinkedToChannel.left()
        }

        log.d { "Creating relation between channel ${channelID.id} and page ${pageID.id}" }
        curd.linkDCChannelToFBPage(null, channel_id = channelID, fb_page_id = pageID)
        Unit.right()
    }

    fun removeChannelPageRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
    ): Outcome<Unit> = run {
        log.d { "Removing relation between channel ${channelID.id} and page ${pageID.id}" }
        curd.unlinkDCChannelToFBPage(channel_id = channelID, fb_page_id = pageID)
        Unit.right()
    }
}
