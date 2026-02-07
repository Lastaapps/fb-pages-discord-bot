package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.DBPageID
import cz.lastaapps.api.domain.model.id.FBPageID

data class Page(
    val dbId: DBPageID,
    val fbId: FBPageID,
    val name: String,
)

data class PageUI(
    val fbId: FBPageID,
    val name: String,
)
