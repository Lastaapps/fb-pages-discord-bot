package cz.lastaapps.api.domain.model.id

@JvmInline
value class FBPageID(val id: ULong) {
    override fun toString(): String {
        error("Forbidden")
    }
}
