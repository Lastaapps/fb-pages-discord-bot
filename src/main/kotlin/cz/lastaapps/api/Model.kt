package cz.lastaapps.api

data class AuthorizedPageFromUser(
    val userID: String,
    val userName: String,
    val userAccessToken: String,
    val pageID: String,
    val pageName: String,
    val pageAccessToken: String,
)

data class AuthorizedPage(
    val id: String,
    val name: String,
    val accessToken: String,
)
