package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.presentation.RestAPI

class GetOAuthLink(
    private val restAPI: RestAPI,
) {
    operator fun invoke(): String = restAPI.oauthUserEndpoint()
}
