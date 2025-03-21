package cz.lastaapps.api.domain.model.token

@JvmInline
value class AppAccessToken(val token: String) {
    override fun toString(): String {
        error("Forbidden use of toString() on a value class ${this::class.simpleName}")
    }
}

fun AppAccessToken.toPageAccessToken() = PageAccessToken(token)
