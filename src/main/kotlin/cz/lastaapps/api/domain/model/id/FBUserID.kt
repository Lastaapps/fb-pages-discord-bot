package cz.lastaapps.api.domain.model.id

@JvmInline
value class FBUserID(val id: ULong) {
    override fun toString(): String =
        if (ENABLE_ID_PRINT) id.toString() else
        error("Forbidden")
}
