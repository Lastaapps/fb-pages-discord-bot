package cz.lastaapps.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DiscordAPI(
    private val config: AppConfig,
) {
    fun start(scope: CoroutineScope) {
        // TODO stop when parent scope stops
        scope.launch {
        }
    }

    fun getChannelName(channelID: String): String = "Channel name"

    fun postPostAndEvents(
        channelID: String,
        posts: Pair<PagePost, List<Event>>,
    ) {
    }
}
