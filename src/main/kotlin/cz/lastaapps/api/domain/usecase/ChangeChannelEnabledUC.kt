package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.id.DCChannelID

class ChangeChannelEnabledUC(
    private val repo: ManagementRepo,
) {
    @Suppress("NAME_SHADOWING")
    suspend operator fun invoke(
        channelID: DCChannelID,
        enabled: Boolean,
    ): Outcome<Unit> =
        either {
            val channelID = repo.getDiscordChannelID(channelID).bind()
            repo.changeChannelEnabled(channelID, enabled)
        }
}
