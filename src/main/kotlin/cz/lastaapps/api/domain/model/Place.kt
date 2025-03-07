package cz.lastaapps.api.domain.model

import io.ktor.http.Url
import kotlin.math.absoluteValue

data class Place(
    val link: Url?,
    val name: String?,
    val location: Location?,
) {
    data class Location(
        val city: String?,
        val country: String?,
        val latitude: Double?,
        val longitude: Double?,
    ) {
        val formattedLatitude
            get() =
                latitude?.let { coordinate ->
                    "%.6f%s".format(coordinate.absoluteValue, if (coordinate >= 0) "N" else "S")
                }
        val formattedLongitude
            get() =
                longitude?.let { coordinate ->
                    "%.6f%s".format(coordinate.absoluteValue, if (coordinate >= 0) "E" else "W")
                }
    }
}
