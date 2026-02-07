package cz.lastaapps.api.domain.model.token

/**
 * Represents token representing access to user account or to a system user
 */
@JvmInline
value class UserAccessToken(
    val token: String,
) {
    override fun toString(): String {
        error("Forbidden use of toString() on a value class ${this::class.simpleName}")
    }
}
