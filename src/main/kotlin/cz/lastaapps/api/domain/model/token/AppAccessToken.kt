package cz.lastaapps.api.domain.model.token

@JvmInline
value class AppAccessToken(val token: String)

fun AppAccessToken.toPageAccessToken() = PageAccessToken(token)
