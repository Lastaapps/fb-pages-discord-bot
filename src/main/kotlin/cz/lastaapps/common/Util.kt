package cz.lastaapps.common

import io.ktor.http.decodeURLQueryComponent

fun decodeFacebookUrl(url: String): String =
    url
        .splitToSequence('&', '?')
        .first { it.startsWith("u=") }
        .removePrefix("u=")
        .decodeURLQueryComponent()
        .splitToSequence('&', '?')
        .filterNot { it.startsWith("fbclid=") }
        .let { parts ->
            parts.first() + (parts.drop(1).joinToString("&").takeUnless { it.isEmpty() }?.let { "?$it" } ?: "")
        }
