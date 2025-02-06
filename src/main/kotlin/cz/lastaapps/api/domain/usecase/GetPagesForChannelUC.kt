package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.toPages

class GetPagesForChannelUC(
    private val repo: ManagementRepo,
) {
    suspend operator fun invoke(channelID: DCChannelID): Outcome<List<Page>> = either {
        @Suppress("NAME_SHADOWING")
        val channelID = repo.getDiscordChannelID(channelID).bind()
        repo.loadAuthorizedPagesForChannel(channelID).toPages()
    }
}
