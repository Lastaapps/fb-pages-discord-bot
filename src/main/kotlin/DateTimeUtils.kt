package cz.lastaapps

import kotlinx.datetime.*
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding

private val CET = TimeZone.of("Europe/Prague")

private val postPublishedAtFormat = DateTimeComponents.Format {
    // May 27 at 6:13 PM
    monthName(MonthNames.ENGLISH_FULL)
    chars(" ")
    dayOfMonth(padding = Padding.NONE)
//    optional(", 2025") {
//        chars(", ")
//        year()
//    }
    chars(" at ")
    amPmHour(padding = Padding.NONE)
    chars(":")
    minute(padding = Padding.ZERO)
    chars(" ")
    amPmMarker("AM", "PM")
}

fun String.parsePostPublishedAt(
    timeZone: TimeZone = CET,
    yearNow: Int = Clock.System.now().toLocalDateTime(timeZone).year,
): Instant = postPublishedAtFormat.parse(this)
    .also { it.year = it.year ?: yearNow }
    .toLocalDateTime()
    .toInstant(timeZone)
