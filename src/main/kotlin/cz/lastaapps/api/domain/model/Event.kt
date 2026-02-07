package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.FBEventID
import io.ktor.http.Url
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

data class Event(
    val id: FBEventID,
    val link: ResolvedLink,
    val startAt: Instant?,
    val endAt: Instant?,
    val timezone: TimeZone,
    val name: String?,
    val description: String?,
    val place: Place?,
    val coverPhotoLink: Url?,
    val isOnline: Boolean,
)
