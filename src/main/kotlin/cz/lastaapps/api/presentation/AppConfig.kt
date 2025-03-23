@file:Suppress("SameParameterValue")

package cz.lastaapps.api.presentation

import co.touchlab.kermit.Severity
import io.ktor.client.plugins.logging.LogLevel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AppConfig(
    val facebook: Facebook,
    val discord: Discord,
    val server: Server,
    val logging: Logging,
    val concurrency: Concurrency,
    val networking: Networking,
    val databaseFileName: String,
    val adminToken: String,
    val interval: Duration,
) {
    data class Facebook(
        val appID: String,
        val appSecret: String,
        val enabledPublicContent: Boolean,
        val enabledUserTokens: Boolean,
        val enabledLogin: Boolean,
        val loginConfigID: String?,
        val loginRedirectURL: String?,
    ) {
        init {
            if (enabledLogin) {
                check(loginConfigID != null && loginRedirectURL != null) {
                    println("If facebook login is enabled, loginConfigID and loginRedirectURL must be set!")
                }
            }
        }
    }

    data class Discord(
        val token: String,
    )

    data class Server(
        val host: String,
        val port: Int,
        val endpointPublic: String,
        val endpointOAuth: String,
        val hostURL: String,
    )

    data class Logging(
        val logLevel: Severity,
        val logLevelHttp: LogLevel,
    )

    data class Concurrency(
        val fetchPages: Int,
        val postPosts: Int,
        val resolvePosts: Int,
    )

    data class Networking(
        val compressResponses: Boolean,
        val clientHttpEngine: HttpEngine,
    ) {
        enum class HttpEngine { CIO, OKHTTP, }
    }

    companion object {
        fun fromEnv() =
            AppConfig(
                facebook =
                    Facebook(
                        appID = str("FACEBOOK_APP_ID"),
                        appSecret = str("FACEBOOK_APP_SECRET"),
                        enabledPublicContent = bool("FACEBOOK_PUBLIC_ENABLED"),
                        enabledUserTokens = bool("FACEBOOK_USER_TOKENS_ENABLED"),
                        enabledLogin = bool("FACEBOOK_LOGIN_ENABLED"),
                        loginConfigID = str("FACEBOOK_LOGIN_CONFIG_ID"),
                        loginRedirectURL = str("FACEBOOK_LOGIN_REDIRECT_URL"),
                    ),
                discord =
                    Discord(
                        token = str("DISCORD_BOT_TOKEN"),
                    ),
                server =
                    Server(
                        host = str("SERVER_HOST"),
                        port = int("SERVER_PORT"),
                        endpointPublic = str("SERVER_ENDPOINT_PUBLIC").withSlash(),
                        endpointOAuth = str("SERVER_ENDPOINT_OAUTH").withSlash(),
                        hostURL = str("SERVER_HOST_URL"),
                    ),
                logging = Logging(
                    logLevel = str("LOG_LEVEL").lowercase()
                        .let { env -> Severity.entries.first { it.name.lowercase() == env } },
                    logLevelHttp = str("LOG_LEVEL_HTTP").lowercase()
                        .let { env -> LogLevel.entries.first { it.name.lowercase() == env } },
                ),
                concurrency = Concurrency(
                    fetchPages = int("CONCURRENCY_FETCH_PAGES", 1),
                    postPosts = int("CONCURRENCY_POST_POSTS", 1),
                    resolvePosts = int("CONCURRENCY_RESOLVE_POSTS", 3),
                ),
                networking = Networking(
                    compressResponses = bool("COMPRESS_RESPONSES", true),
                    clientHttpEngine = Networking.HttpEngine.entries.first {
                        str("CLIENT_HTTP_ENGINE", Networking.HttpEngine.CIO.name).lowercase() == it.name.lowercase()
                    },
                ),
                databaseFileName = str("DATABASE_FILENAME"),
                adminToken = str("ADMIN_TOKEN"),
                interval = int("INTERVAL_SEC").seconds,
            )

        private fun key(key: String) = "FB_DC_API_$key"

        private fun str(key: String) =
            strNull(key) ?: error("The env var $key cannot be blank")

        private fun str(key: String, default: String) = strNull(key) ?: default

        private fun strNull(key: String): String? = System.getenv(key(key))?.takeIf { it.isNotBlank() }

        private fun int(key: String) = str(key).toInt()

        private fun int(key: String, default: Int) = strNull(key)?.toInt() ?: default

        private fun bool(key: String) = str(key).toBoolean()

        private fun bool(key: String, default: Boolean) = strNull(key)?.toBoolean() ?: default

        private fun String.withSlash() = if (this.startsWith("/")) this else "/$this"
    }
}
