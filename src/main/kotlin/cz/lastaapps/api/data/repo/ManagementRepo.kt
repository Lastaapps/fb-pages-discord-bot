package cz.lastaapps.api.data.repo

import arrow.core.None
import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.filterOption
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.AppDatabase
import cz.lastaapps.api.data.api.DiscordAPI
import cz.lastaapps.api.data.api.DiscordKord
import cz.lastaapps.api.data.api.FBAuthAPI
import cz.lastaapps.api.domain.AppDCPermissionSet
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.error.LogicError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.model.AuthorizedPage
import cz.lastaapps.api.domain.model.AuthorizedPageFromUser
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.PageUI
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
    private val discordAPI: DiscordAPI,
    private val authAPI: FBAuthAPI,
) {
    private val log = Logger.withTag("ManagementRepo")

    private inline val curd get() = database.database.schemaQueries
    private inline val queries get() = database.database.queriesQueries

    private inline val kord get() = discordKord.kord

    suspend fun getDiscordChannelID(channelID: DCChannelID): Outcome<DBChannelID> =
        run {
            curd
                .getByDCID(channelID)
                .executeAsOneOrNull()
                ?.let { return it.right() }

            val name =
                kord.rest.channel
                    .getChannel(channelID.toSnowflake())
                    .name.value!!

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
    ): Outcome<DBPageID> =
        either {
            @Suppress("NAME_SHADOWING")
            val allowUnauthorized = allowUnauthorized ?: config.facebook.enabledPublicContent

            // if the page reference exists, get it
            if (allowUnauthorized) {
                curd.getPageByFBId(fbPageID).executeAsOneOrNull()
            } else {
                queries.getAuthorizedPageByFBId(fbPageID).executeAsOneOrNull()
            }?.let { return@either it }

            if (!allowUnauthorized || !allowFetch) {
                return LogicError.PageNotAuthorized.left()
            }

            val appToken = appTokenProvider.provide().bind()
            val metadata =
                authAPI.getPageMetadata(pageID = fbPageID, pageAccessToken = appToken.toPageAccessToken()).bind()
            curd.transactionWithResult {
                curd.createFBPage(null, name = metadata.name, metadata.fbId)
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

    fun getPageByID(id: DBPageID): Outcome<Option<PageUI>> =
        run {
            curd
                .getPageByID(id)
                .executeAsOneOrNull()
                ?.let { PageUI(fbId = it.fb_id, name = it.name) }
                .toOption()
                .right()
        }

    fun getPageIDByName(name: String): Outcome<Option<DBPageID>> =
        run {
            curd
                .getPageIDByName(name)
                .executeAsOneOrNull()
                .toOption()
                .right()
        }

    suspend fun loadChannelsWithInfo(): List<Tuple5<DBChannelID, DCChannelID, String, Boolean, Option<String>>> =
        curd
            .getAllDCChannels()
            .executeAsList()
            .map { (dbId, name, dcId, channelEnabled) ->
                val serverName =
                    discordAPI
                        .getServerNameForChannel(dcId)
                        .onLeft { log.e(it) { "Failed to obtain server name for channel ${dcId.id} ($name)" } }
                Tuple5(dbId, dcId, name, channelEnabled, serverName.fold({ None }, { it.some() }))
            }

    fun loadAuthorizedPages(): List<AuthorizedPage> =
        queries
            .getAuthorizedPages()
            .executeAsList()
            .map { (dbPageID, pageName, fbPageID, pageAccessToken) ->
                AuthorizedPage(dbId = dbPageID, fbId = fbPageID, name = pageName, accessToken = pageAccessToken)
            }

    suspend fun loadPagesForChannel(channelID: DBChannelID): Outcome<List<Page>> =
        either {
            queries
                .getPagesForChannel(channelID) { dbPageID, pageName, fbPageID ->
                    Page(dbId = dbPageID, fbId = fbPageID, name = pageName).some()
                }.executeAsList()
                .filterOption()
        }

    suspend fun loadAuthorizedPagesForChannel(channelID: DBChannelID): Outcome<List<AuthorizedPage>> =
        either {
            val hasPublic = config.facebook.enabledPublicContent
            val appToken =
                if (hasPublic) {
                    appTokenProvider.provide().bind().toPageAccessToken()
                } else {
                    null
                }
            queries
                .getPagesForChannelWithTokens(channelID) { dbPageID, pageName, fbPageID, pageAccessToken ->
                    // TODO notify user somehow, probably IOR
                    val token =
                        pageAccessToken ?: appToken ?: run {
                            log.e { "Page $pageName is requested by channel ${channelID.id}, but it's not authorized" }
                            return@getPagesForChannelWithTokens None
                        }
                    AuthorizedPage(dbId = dbPageID, fbId = fbPageID, name = pageName, accessToken = token).some()
                }.executeAsList()
                .filterOption()
        }

    fun deleteChannel(channelID: DBChannelID): Outcome<Unit> =
        curd.transactionWithResult {
            curd.deleteDCChannel(channelID)
            Unit.right()
        }

    fun createChannelPageRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
    ): Outcome<Unit> =
        curd.transactionWithResult {
            if (curd.isLinkedDCChannelToFBPage(channelID, pageID).executeAsOneOrNull() != null) {
                return@transactionWithResult LogicError.PageAlreadyLinkedToChannel.left()
            }

            log.d { "Creating relation between channel ${channelID.id} and page ${pageID.id}" }
            curd.linkDCChannelToFBPage(null, channel_id = channelID, fb_page_id = pageID)
            Unit.right()
        }

    fun removeChannelPageRelation(
        channelID: DBChannelID,
        pageID: DBPageID,
    ): Outcome<Unit> =
        run {
            log.d { "Removing relation between channel ${channelID.id} and page ${pageID.id}" }
            curd.unlinkDCChannelToFBPage(channel_id = channelID, fb_page_id = pageID)
            Unit.right()
        }

    fun changeChannelEnabled(
        channelID: DBChannelID,
        enabled: Boolean,
    ): Outcome<Unit> =
        run {
            log.d { "Making channel ${channelID.id} ${if (enabled) "enabled" else "disabled"}" }
            curd.changeChannelEnabled(id = channelID, enabled = enabled)
            Unit.right()
        }

    suspend fun checkAllPermissionKinds(channelID: DCChannelID): Outcome<Map<AppDCPermissionSet, Boolean>> =
        discordAPI
            .checkBotPermissions(channelID, AppDCPermissionSet.entries)
            .onLeft { log.e(it) { "Failed to obtain bot's permissions for channel ${channelID.id}" } }
}
