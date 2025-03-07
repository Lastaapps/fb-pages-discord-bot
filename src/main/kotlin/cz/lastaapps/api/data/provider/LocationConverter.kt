package cz.lastaapps.api.data.provider

import arrow.core.Either
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.model.FBPlace
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.model.Place
import io.ktor.http.Url

class LocationConverter(
    private val linkResolver: LinkResolver,
) {
    suspend fun convertPlace(place: FBPlace): Place? =
        place.toURL()
            // The error is suppressed here
            ?.let { link ->
                linkResolver.resolve(link).fold(
                    {
                        log.e(it) { "Failed to resolve place id link" }
                        Either.catch { Url(link) }.getOrNull()
                    },
                    { it.link },
                )
            }
            ?.let { link ->
                Place(
                    link = link,
                    name = place.name,
                    location = place.location?.toDomain(),
                )
            }

    companion object {
        private val log = Logger.withTag("LocationConverter")
    }
}
