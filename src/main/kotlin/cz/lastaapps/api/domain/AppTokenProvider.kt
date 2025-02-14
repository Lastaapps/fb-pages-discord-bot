package cz.lastaapps.api.domain

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.right
import cz.lastaapps.api.data.FBAuthAPI
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.token.AppAccessToken
import cz.lastaapps.api.presentation.AppConfig

class AppTokenProvider(
    private val config: AppConfig,
    private val authApi: FBAuthAPI,
) {
    private var token: Option<AppAccessToken> = None

    // This should be under mutex, but I don't care if few requests are accidentally made at the beginning.
    suspend fun provide(): Outcome<AppAccessToken> = run {
        check(config.facebook.enabledPublicContent) { "Login using AppAccessToken is not enabled" }

        when (val token = token) {
            None -> {}
            is Some -> return token.value.right()
        }

        authApi.getAppAccessToken()
            .onRight { token = Some(it) }
    }
}
