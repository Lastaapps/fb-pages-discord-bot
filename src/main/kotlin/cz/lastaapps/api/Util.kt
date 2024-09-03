package cz.lastaapps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

// yes, I don't like this either, but I'm to lazy to do it properly
fun String.createdTimeToInstant() =
//    java.time.OffsetDateTime.parse(this).toInstant().toKotlinInstant()
    Json.decodeFromString<Instant>(removeSuffix("+0000").plus("Z").let { "\"$it\"" })

fun String.idToFacebookURL() = "https://www.facebook.com/$this"

fun isFBLink(link: String) =
    link.startsWith("https://l.facebook.com") or
        link.startsWith("https://lm.facebook.com")
