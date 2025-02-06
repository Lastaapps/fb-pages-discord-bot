package cz.lastaapps.api.domain

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import cz.lastaapps.api.data.FBAuthAPI
import cz.lastaapps.api.domain.model.token.AppAccessToken
import cz.lastaapps.api.presentation.AppConfig

class AppTokenProvider(
    private val config: AppConfig,
    private val authApi: FBAuthAPI,
) {
    private var token: Option<AppAccessToken> = None

    // This should be under mutex, but I don't care if (if even) few requests are made at the beginning.
    suspend fun provide(): AppAccessToken = run {
        check(config.facebook.enabledPublicContent) { "Login using AppAccessToken is not enabled" }

        when (val token = token) {
            None -> {}
            is Some -> return token.value
        }

        authApi.getAppAccessToken()
            .also { token = Some(it) }
    }
}
