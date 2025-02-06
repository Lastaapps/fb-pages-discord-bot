package cz.lastaapps.api.domain.model.id

@JvmInline
value class FBEventID(val id: String) {
    override fun toString(): String {
        error("Forbidden")
    }
}
