package cz.lastaapps.api.domain.model.id

@JvmInline
value class DBChannelID(
    val id: ULong,
) {
    override fun toString(): String =
        if (ENABLE_ID_PRINT) {
            id.toString()
        } else {
            error("Forbidden use of toString() on a value class ${this::class.simpleName}")
        }
}
