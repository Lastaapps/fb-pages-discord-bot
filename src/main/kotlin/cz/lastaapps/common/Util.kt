package cz.lastaapps.common

import dev.kord.common.Color
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
            parts.first() + (
                parts
                    .drop(1)
                    .joinToString("&")
                    .takeUnless { it.isEmpty() }
                    ?.let { "?$it" } ?: ""
            )
        }

val imageExtensions =
    listOf(
        ".jpg",
        ".jpeg",
        ".png",
        ".webp",
        ".gif",
    )

val colorsSet =
    listOf(
        Color(0, 0, 0),
        Color(255, 255, 255),
        Color(255, 0, 0),
        Color(0, 255, 0),
        Color(0, 0, 255),
        Color(255, 255, 0),
        Color(255, 165, 0),
        Color(128, 0, 128),
        Color(255, 192, 203),
        Color(0, 128, 128),
        Color(128, 128, 128),
        Color(139, 69, 19),
        Color(255, 140, 0),
        Color(0, 255, 127),
        Color(0, 102, 204),
        Color(238, 130, 238),
        Color(255, 204, 0),
        Color(153, 0, 153),
        Color(204, 0, 0),
        Color(255, 99, 71),
        Color(255, 182, 193),
        Color(34, 139, 34),
        Color(102, 0, 0),
        Color(184, 115, 51),
        Color(0, 128, 128),
        Color(255, 0, 255),
        Color(128, 128, 0),
    )
