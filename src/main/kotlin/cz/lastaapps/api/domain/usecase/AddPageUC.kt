package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.FBPageID

class AddPageUC(
    private val repo: ManagementRepo,
) {
    @Suppress("NAME_SHADOWING")
    suspend operator fun invoke(
        channelID: DCChannelID,
        pageID: FBPageID,
    ): Outcome<Page> = either {
        val channelID = repo.getDiscordChannelID(channelID).bind()
        val pageID = repo.getFBPageID(pageID).bind()
        repo.createChannelPageRelation(channelID, pageID).bind()
        repo.getPageByID(pageID).bind().getOrNull()!!
    }
}
