package cz.lastaapps.api.domain.model.id

@JvmInline
value class FBPostID(val id: String) {
    override fun toString(): String =
        if (ENABLE_ID_PRINT) id.toString() else
        error("Forbidden")
}
