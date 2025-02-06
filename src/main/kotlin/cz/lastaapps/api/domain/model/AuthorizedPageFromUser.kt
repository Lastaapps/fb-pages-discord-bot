package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBUserID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.UserAccessToken

data class AuthorizedPageFromUser(
    val userID: FBUserID,
    val userName: String,
    val userAccessToken: UserAccessToken,
    val pageID: FBPageID,
    val pageName: String,
    val pageAccessToken: PageAccessToken,
)
