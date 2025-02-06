package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.token.PageAccessToken

data class AuthorizedPage(
    val dbId: DBPageID,
    val fbId: FBPageID,
    val name: String,
    val accessToken: PageAccessToken,
)

fun Collection<AuthorizedPage>.toPages() = map { Page(fbId = it.fbId, name = it.name) }
