package cz.lastaapps.api.data.api

sealed interface AppStrings {
    val albumSubtitle: String
    val albumTitle: String
    val eventNoEmbedDescription: String
    val eventOnlineSubtitle: String
    val eventOnlineTitle: String
    val eventPlaceTitle: String
    val eventTimeTitle: String
    val linksTitle: String
    val unknownLocation: String
}

object AppStringsCZ : AppStrings {
    override val albumSubtitle: String = "Tento příspěvek skrývá více fotek/videí"
    override val albumTitle: String = "Album"
    override val eventNoEmbedDescription: String = "This post cannot be sadly process by the bot."
    override val eventOnlineSubtitle: String = "Více info v příspěvku"
    override val eventOnlineTitle: String = "Online událost"
    override val eventPlaceTitle: String = "Kde?"
    override val eventTimeTitle: String = "Kdy?"
    override val linksTitle: String = "Odkazy"
    override val unknownLocation: String = "Neznámo kde"
}

object AppStringsEN : AppStrings {
    override val albumSubtitle: String = "This post contains multiple photos/videos"
    override val albumTitle: String = "Album"
    override val eventNoEmbedDescription: String = "Unfortunately, this post cannot be processed by the bot."
    override val eventOnlineSubtitle: String = "More info in the post"
    override val eventOnlineTitle: String = "Online Event"
    override val eventPlaceTitle: String = "Where?"
    override val eventTimeTitle: String = "When?"
    override val linksTitle: String = "Links"
    override val unknownLocation: String = "Unknown location"
}

