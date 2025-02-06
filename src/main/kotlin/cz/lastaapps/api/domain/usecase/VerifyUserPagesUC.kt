package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.FBAuthAPI
import cz.lastaapps.api.data.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.token.UserAccessToken

class VerifyUserPagesUC(
    private val api: FBAuthAPI,
    private val repo: ManagementRepo,
) {
    suspend operator fun invoke(
        token: UserAccessToken,
    ): Outcome<List<Page>> = either {
        val authorizedPages = api.grantAccessToUserPages(token).bind()
        authorizedPages.forEach {
            repo.storeAuthorizedPage(it)
        }
        authorizedPages.map {
            Page(fbId = it.pageID, name = it.pageName)
        }
    }
}
