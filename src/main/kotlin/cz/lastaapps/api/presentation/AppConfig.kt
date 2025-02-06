package cz.lastaapps.api.presentation

data class AppConfig(
    val setupMode: Boolean,
    val facebook: Facebook,
    val discord: Discord,
    val server: Server,
    val databaseFileName: String,
    val adminToken: String,
    val intervalSec: Int,
) {
    data class Facebook(
        val appID: String,
        val appSecret: String,
        val enabledPublicContent: Boolean,
        val enabledSystemUser: Boolean,
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

    companion object {
        fun fromEnv() =
            AppConfig(
                setupMode = bool("SETUP_MODE"),
                facebook =
                    Facebook(
                        appID = str("FACEBOOK_APP_ID"),
                        appSecret = str("FACEBOOK_APP_SECRET"),
                        enabledPublicContent = str("FACEBOOK_PUBLIC_ENABLED").toBoolean(),
                        enabledSystemUser = str("FACEBOOK_SYSTEM_USER_ENABLED").toBoolean(),
                        enabledLogin = str("FACEBOOK_LOGIN_ENABLED").toBoolean(),
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
                databaseFileName = str("DATABASE_FILENAME"),
                adminToken = str("ADMIN_TOKEN"),
                intervalSec = int("INTERVAL_SEC"),
            )

        private fun key(key: String) = "FB_DC_API_$key"

        private fun str(key: String) =
            strNull(key).also { check(it.isNotBlank()) { "The env var $key cannot be blank" } }

        private fun strNull(key: String) = System.getenv(key(key))

        private fun int(key: String) = str(key).toInt()

        private fun bool(key: String) = str(key).toBoolean()

        private fun String.withSlash() = if (this.startsWith("/")) this else "/$this"
    }
}
