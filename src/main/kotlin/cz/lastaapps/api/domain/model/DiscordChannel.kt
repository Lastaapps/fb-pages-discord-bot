package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.DBChannelID
import cz.lastaapps.api.domain.model.id.DCChannelID

data class DiscordChannel(
    val dbId: DBChannelID,
    val name: String,
    val dcId: DCChannelID,
    val enabled: Boolean,
)
