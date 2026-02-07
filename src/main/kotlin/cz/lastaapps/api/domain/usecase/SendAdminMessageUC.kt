package cz.lastaapps.api.domain.usecase

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.api.DiscordAPI
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.id.DCChannelID
import cz.lastaapps.api.presentation.AppConfig

class SendAdminMessageUC(
    private val dcApi: DiscordAPI,
    private val config: AppConfig,
) {
    private val log = Logger.withTag("SendAdminMessageUC")

    @Suppress("NAME_SHADOWING")
    suspend operator fun invoke(
        channelID: DCChannelID,
        message: String,
    ): Outcome<Unit> {
        log.i { "Sending admin message to channel ${channelID.id}: '${message.replace("\n", "\\n")}'" }

        val fullMessage =
            """
            ### Bot Administrator Notice
            *This message is from the bot administrator. It indicates that your setup may not be correct.*
            *Please note: The administrator cannot read responses sent to this channel. **Do not reply here.***
            *If you require assistance resolving the issue, please contact the administrator at ${config.adminContact}.*
            *Failure to comply with the administrator's request may result in the bot being disabled in this channel.*
            *You may delete this message once the issue is understood and addressed.*
            """.trimIndent() + "\n\n" + message
        return dcApi.sendSimpleMessage(channelID, fullMessage, allowEmbeds = false)
    }
}
