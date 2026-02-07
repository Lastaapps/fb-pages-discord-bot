package cz.lastaapps.api.domain.usecase

import arrow.core.flatten
import cz.lastaapps.api.data.api.FBDataAPI
import cz.lastaapps.api.domain.AppTokenProvider

class SearchPagesUC(
    private val api: FBDataAPI,
    private val tokenProvider: AppTokenProvider,
) {
    suspend operator fun invoke(name: String) =
        tokenProvider
            .provide()
            .map { api.searchPages(it, name) }
            .flatten()
}
