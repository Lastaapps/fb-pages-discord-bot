package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.data.FBAuthAPI

class GetOAuthLink(
    private val authApi: FBAuthAPI,
) {
    operator fun invoke(): String = authApi.createOAuthURL()
}
