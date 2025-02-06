package cz.lastaapps.api.domain.model.id

@JvmInline
value class DCMessageID(val id: ULong) {
    override fun toString(): String {
        error("Forbidden")
    }
}
