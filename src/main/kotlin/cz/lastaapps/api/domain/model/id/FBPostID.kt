package cz.lastaapps.api.domain.model.id

@JvmInline
value class FBPostID(
    val id: String,
) {
    override fun toString(): String =
        if (ENABLE_ID_PRINT) {
            id
        } else {
            error("Forbidden use of toString() on a value class ${this::class.simpleName}")
        }
}
