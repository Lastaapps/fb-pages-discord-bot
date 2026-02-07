package cz.lastaapps.api.data

import api.DC_Channel
import api.DC_Channel_FB_Page
import api.FB_Page
import api.FB_Page_FB_Page_Token
import api.FB_Page_Token
import api.Posted_Post
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.touchlab.kermit.Logger
import cz.lastaapps.api.Database
import cz.lastaapps.api.domain.model.id.DBChannelID
import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.DBPageTokenID
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.DCMessageID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBPostID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.presentation.AppConfig

@JvmInline
value class AppDatabase(
    val database: Database,
) {
    companion object Factory {
        private val log = Logger.withTag("AppDatabase")

        fun create(config: AppConfig) = create(config.databaseFileName)

        fun create(dbName: String): AppDatabase = AppDatabase(createDatabase(createDriver(dbName)))

        private val fbPageIDAdapter =
            object : ColumnAdapter<FBPageID, Long> {
                override fun decode(databaseValue: Long): FBPageID = FBPageID(databaseValue.toULong())

                override fun encode(value: FBPageID): Long = value.id.toLong()
            }
        private val dbPageIDAdapter =
            object : ColumnAdapter<DBPageID, Long> {
                override fun decode(databaseValue: Long): DBPageID = DBPageID(databaseValue.toULong())

                override fun encode(value: DBPageID): Long = value.id.toLong()
            }
        private val dbChannelIDAdapter =
            object : ColumnAdapter<DBChannelID, Long> {
                override fun decode(databaseValue: Long): DBChannelID = DBChannelID(databaseValue.toULong())

                override fun encode(value: DBChannelID): Long = value.id.toLong()
            }
        private val dcChannelIDAdapter =
            object : ColumnAdapter<DCChannelID, Long> {
                override fun decode(databaseValue: Long): DCChannelID = DCChannelID(databaseValue.toULong())

                override fun encode(value: DCChannelID): Long = value.id.toLong()
            }
        private val dbPageTokenIDAdapter =
            object : ColumnAdapter<DBPageTokenID, Long> {
                override fun decode(databaseValue: Long): DBPageTokenID = DBPageTokenID(databaseValue.toULong())

                override fun encode(value: DBPageTokenID): Long = value.id.toLong()
            }
        private val fbPostIdAdapter =
            object : ColumnAdapter<FBPostID, String> {
                override fun decode(databaseValue: String): FBPostID = FBPostID(databaseValue)

                override fun encode(value: FBPostID): String = value.id
            }
        private val dcMessageIDAdapter =
            object : ColumnAdapter<DCMessageID, Long> {
                override fun decode(databaseValue: Long): DCMessageID = DCMessageID(databaseValue.toULong())

                override fun encode(value: DCMessageID): Long = value.id.toLong()
            }
        private val pageAccessTokenAdapter =
            object : ColumnAdapter<PageAccessToken, String> {
                override fun decode(databaseValue: String): PageAccessToken = PageAccessToken(databaseValue)

                override fun encode(value: PageAccessToken): String = value.token
            }

        private fun createDatabase(driver: SqlDriver): Database =
            Database(
                driver,
                FB_PageAdapter =
                    FB_Page.Adapter(
                        fb_idAdapter = fbPageIDAdapter,
                        idAdapter = dbPageIDAdapter,
                    ),
                DC_ChannelAdapter =
                    DC_Channel.Adapter(
                        idAdapter = dbChannelIDAdapter,
                        dc_idAdapter = dcChannelIDAdapter,
                    ),
                DC_Channel_FB_PageAdapter =
                    DC_Channel_FB_Page.Adapter(
                        channel_idAdapter = dbChannelIDAdapter,
                        fb_page_idAdapter = dbPageIDAdapter,
                    ),
                FB_Page_FB_Page_TokenAdapter =
                    FB_Page_FB_Page_Token.Adapter(
                        fb_page_idAdapter = dbPageIDAdapter,
                        fb_page_token_idAdapter = dbPageTokenIDAdapter,
                    ),
                FB_Page_TokenAdapter =
                    FB_Page_Token.Adapter(
                        idAdapter = dbPageTokenIDAdapter,
                        tokenAdapter = pageAccessTokenAdapter,
                    ),
                Posted_PostAdapter =
                    Posted_Post.Adapter(
                        channel_idAdapter = dbChannelIDAdapter,
                        page_idAdapter = dbPageIDAdapter,
                        fb_idAdapter = fbPostIdAdapter,
                        dc_message_idAdapter = dcMessageIDAdapter,
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
}
