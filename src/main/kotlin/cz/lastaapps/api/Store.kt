package cz.lastaapps.api

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arrow.core.Tuple4

class Store(
    config: AppConfig,
) {
    private val database: Database = createDatabase(createDriver(config.databaseFileName))
    private val queries get() = database.schemaQueries

    fun storeAuthorizedPage(authorizedPage: AuthorizedPageFromUser) {
        println("Storing $authorizedPage")
        with(authorizedPage) {
            queries.transaction {
                queries.insertAuthenticatedUser(userID, userName, userAccessToken)
                queries.insertAuthenticatedPage(pageID, pageName, pageAccessToken, userID)
            }
        }
    }

    fun createDiscordChannel(channelID: String) {
        println("Creating channel $channelID")
        queries.insertDiscordChannel(channelID)
    }

    /**
     * Return discord channel ID and page access token of the pages related to the channel
     */
    fun loadPageDiscordPairs(): Map<String, List<AuthorizedPage>> =
        queries
            .selectChannelsWithPages()
            .executeAsList()
            .map { (channelID, pageID, pageName, pageAccessToken) ->
                channelID to AuthorizedPage(id = pageID, name = pageName, accessToken = pageAccessToken)
            }.groupBy { it.first }
            .mapValues { (_, value) -> value.map { it.second } }

    fun createChannelPageRelation(
        channelID: String,
        pageID: String,
    ) {
        queries.assignPageToDiscordChannel(channel_id = channelID, page_id = pageID)
    }

    fun getChannelPageRelation() =
        queries
            .selectChannelsWithPages()
            .executeAsList()
            .map { Tuple4(it.channel_id, it.page_id, it.page_name, it.page_access_token) }

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

private fun createDatabase(driver: SqlDriver): Database = Database(driver)

private fun createDriver(dbName: String): SqlDriver {
    val driver: SqlDriver =
        JdbcSqliteDriver(
            "jdbc:sqlite:$dbName",
        )
    Database.Schema.create(driver)
    return driver
}
