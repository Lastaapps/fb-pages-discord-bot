package cz.lastaapps.api.domain.model.id

import dev.kord.common.entity.Snowflake

@JvmInline
value class DCChannelID(val id: ULong) {
    override fun toString(): String {
        error("Forbidden")
    }
}

fun Snowflake.toChannelID() = DCChannelID(this.value)

fun DCChannelID.toSnowflake() = Snowflake(this.id)
