package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.id.DCChannelID

class NukeChannelUC(
    private val repo: ManagementRepo,
) {
    @Suppress("NAME_SHADOWING")
    suspend operator fun invoke(channelID: DCChannelID): Outcome<Unit> =
        either {
            val channelID = repo.getDiscordChannelID(channelID).bind()
            repo.loadPagesForChannel(channelID).bind().forEach { page ->
                repo.removeChannelPageRelation(channelID, page.dbId).bind()
            }
            repo.deleteChannel(channelID).bind()
        }
}
