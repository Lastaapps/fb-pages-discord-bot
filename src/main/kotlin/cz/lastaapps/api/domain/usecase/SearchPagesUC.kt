package cz.lastaapps.api.domain.usecase

import cz.lastaapps.api.data.FBDataAPI
import cz.lastaapps.api.domain.AppTokenProvider

class SearchPagesUC(
    private val api: FBDataAPI,
    private val tokenProvider: AppTokenProvider,
) {
    suspend operator fun invoke(name: String) = api.searchPages(tokenProvider.provide(), name)
}
