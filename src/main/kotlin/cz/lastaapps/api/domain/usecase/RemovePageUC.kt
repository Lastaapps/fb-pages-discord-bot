package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.PageUI
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.FBPageID

class RemovePageUC(
    private val repo: ManagementRepo,
) {
    @Suppress("NAME_SHADOWING")
    suspend operator fun invoke(
        channelID: DCChannelID,
        pageID: FBPageID,
    ): Outcome<PageUI> =
        either {
            val channelID = repo.getDiscordChannelID(channelID).bind()
            val pageID = repo.getFBPageID(pageID, allowUnauthorized = true).bind()
            repo.removeChannelPageRelation(channelID, pageID).bind()
            repo.getPageByID(pageID).bind().getOrNull()!!
        }
}
