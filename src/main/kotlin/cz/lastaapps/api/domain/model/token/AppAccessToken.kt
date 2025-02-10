package cz.lastaapps.api.domain.model.token

@JvmInline
value class AppAccessToken(val token: String) {
    override fun toString(): String {
        error("Forbidden")
    }
}

fun AppAccessToken.toPageAccessToken() = PageAccessToken(token)
