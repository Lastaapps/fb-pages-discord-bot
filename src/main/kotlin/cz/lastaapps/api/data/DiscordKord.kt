package cz.lastaapps.api.data

import co.touchlab.kermit.Logger
import cz.lastaapps.api.presentation.AppConfig
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@JvmInline
value class DiscordKord private constructor(val kord: Kord) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            log.d { "Starting the DC client..." }
            kord.login()
        }
    }

    companion object {
        private val log = Logger.withTag("DiscordKord")

        suspend fun create(
            config: AppConfig,
        ): DiscordKord {
            val kord = Kord(config.discord.token)
            return DiscordKord(kord)
        }
    }

}

