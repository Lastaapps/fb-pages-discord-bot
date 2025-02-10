package cz.lastaapps.api.domain.model.token

@JvmInline
value class PageAccessToken(val token: String) {
    override fun toString(): String {
        error("Forbidden")
    }
}
