package cz.lastaapps.api.domain.usecase

import arrow.core.raise.either
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.PageUI
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.domain.model.id.FBPageID
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message

class AddPageUC(
    private val repo: ManagementRepo,
) {
    @Suppress("NAME_SHADOWING")
    suspend operator fun invoke(
        channelID: DCChannelID,
        pageID: FBPageID,
    ): Outcome<PageUI> =
        either {
            val channelID = repo.getDiscordChannelID(channelID).bind()
            val pageID = repo.getFBPageID(pageID).bind()
            repo.createChannelPageRelation(channelID, pageID).bind()
            val res = repo.getPageByID(pageID).bind().getOrNull()!!

            Sentry.captureEvent(
                SentryEvent().apply {
                    message =
                        Message().apply {
                            message = "New page added!"
                            params = listOf(res.name, res.fbId.id.toString())
                        }
                    level = SentryLevel.WARNING
                },
            )

            res
        }
}
