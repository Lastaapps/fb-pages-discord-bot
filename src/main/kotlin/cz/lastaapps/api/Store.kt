package cz.lastaapps.api


class Store {
    fun storeAuthorizedPage(
        authorizedPage: AuthorizedPageFromUser,
    ) {
        println("Storing $authorizedPage")
    }

    /**
     * Return discord channel ID and page access token of the pages related to the channel
     */
    fun loadPageDiscordPairs(): Map<String, List<AuthorizedPage>> {
        return mapOf(
            "channel_id" to listOf(
                AuthorizedPage(
                    id = PAGE_ID,
                    name = "Page name",
                    accessToken = PAGE_ACCESS_TOKEN,
                ),
            ),
        )
    }
}
