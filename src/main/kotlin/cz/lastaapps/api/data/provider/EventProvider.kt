package cz.lastaapps.api.data.provider

import arrow.core.None
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.some
import cz.lastaapps.api.data.api.FBDataAPI
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Event
import cz.lastaapps.api.domain.model.id.FBEventID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import io.ktor.http.Url

class EventProvider(
    private val api: FBDataAPI,
    private val resolver: LinkResolver,
    private val locationConverter: LocationConverter,
) {
    suspend fun loadEventData(
        eventID: FBEventID,
        pageAccessToken: PageAccessToken,
    ): Outcome<Option<Event>> = api.loadEventData(eventID, pageAccessToken).flatMap { event ->
        either {
            if (!event.canBePublished()) {
                return@either None
            }

            val link = resolver.resolve(event.toURL()).bind()
            val start = event.startAt
            val end = event.endAt
            val coverPhotoLink = event.coverPhoto?.source?.let { Url(it) }
            val place = event.place?.let { locationConverter.convertPlace(it) }

            Event(
                id = event.fbId,
                link = link,
                startAt = start,
                endAt = end,
                timezone = event.timezone,
                name = event.name,
                description = event.description,
                place = place,
                coverPhotoLink = coverPhotoLink,
                isOnline = event.isOnline,
            ).some()
        }
    }
}
