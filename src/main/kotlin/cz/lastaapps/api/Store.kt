package cz.lastaapps.api

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

class Store(
    config: AppConfig,
) {
    private val database: Database = createDatabase(createDriver(config.databaseFileName))
    private val queries get() = database.schemaQueries

    fun storeAuthorizedPage(authorizedPage: AuthorizedPageFromUser) {
        println("Storing $authorizedPage")
        with(authorizedPage) {
            queries.insertAuthenticatedPage(pageID, pageName, pageAccessToken)
        }
    }

    fun loadAuthenticatedPages(): List<AuthorizedPage> =
        queries
            .selectAllPages()
            .executeAsList()
            .map { (pageID, pageName, pageAccessToken) ->
                AuthorizedPage(id = pageID, name = pageName, accessToken = pageAccessToken)
            }

    /**
     * Return discord channel ID and page access token of the pages related to the channel
     */
    fun loadPageDiscordPairs(): Map<String, List<AuthorizedPage>> =
        queries
            .selectChannelsWithPages()
            .executeAsList()
            .map {
                with(it) {
                    channel_id to AuthorizedPage(id = page_id, name = page_name, accessToken = page_access_token)
                }
            }.groupBy { it.first }
            .mapValues { (_, value) -> value.map { it.second } }

    fun createChannelPageRelation(
        channelID: String,
        pageID: String,
    ) {
        queries.assignPageToDiscordChannel(channel_id = channelID, page_id = pageID)
    }

    fun removeChannelPageRelation(
        channelID: String,
        pageID: String,
    ) {
        queries.removePageToDiscordChannel(channel_id = channelID, page_id = pageID)
    }

    fun createMessagePostRelation(
        messageID: String,
        postID: String,
    ) {
        queries.assignMessageToPost(message_id = messageID, post_id = postID)
    }

    fun getMessagePostRelations(): List<Pair<String, String>> =
        queries
            .selectMessagesWithPosts()
            .executeAsList()
            .map { it.message_id to it.post_id }

    fun getMessagesRelatedToPost(postID: String): List<String> =
        queries
            .selectMessagesForPost(postID)
            .executeAsList()
            .map { it.message_id }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Store.isPostPosted(postID: String): Boolean = getMessagesRelatedToPost(postID).isNotEmpty()

private fun createDatabase(driver: SqlDriver): Database = Database(driver)

private fun createDriver(dbName: String): SqlDriver {
    val driver: SqlDriver =
        JdbcSqliteDriver(
            "jdbc:sqlite:$dbName",
            schema = Database.Schema,
        )
    return driver
}
