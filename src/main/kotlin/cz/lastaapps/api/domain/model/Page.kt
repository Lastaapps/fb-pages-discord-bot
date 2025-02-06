package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.FBPageID

data class Page(
    val fbId: FBPageID,
    val name: String,
)
