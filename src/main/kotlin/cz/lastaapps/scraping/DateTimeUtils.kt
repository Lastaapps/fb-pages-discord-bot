package cz.lastaapps.scraping

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

val CET = TimeZone.of("Europe/Prague")

private val postPublishedAtFormatThisYear =
    DateTimeComponents.Format {
        // May 27 at 6:13 PM
        monthName(MonthNames.ENGLISH_FULL)
        chars(" ")
        dayOfMonth(padding = Padding.NONE)
        chars(" at ")
        amPmHour(padding = Padding.NONE)
        chars(":")
        minute(padding = Padding.ZERO)
        chars(" ")
        amPmMarker("AM", "PM")
    }

private val postPublishedAtFormatPast =
    DateTimeComponents.Format {
        // May 27 at 6:13 PM
        monthName(MonthNames.ENGLISH_FULL)
        chars(" ")
        dayOfMonth(padding = Padding.NONE)
        chars(", ")
        year()
        chars(" at ")
        amPmHour(padding = Padding.NONE)
        chars(":")
        minute(padding = Padding.ZERO)
        chars(" ")
        amPmMarker("AM", "PM")
    }
private val postPublishedAtFormatYesterday =
    DateTimeComponents.Format {
        chars("Yesterday at ")
        amPmHour(padding = Padding.NONE)
        chars(":")
        minute(padding = Padding.ZERO)
        chars(" ")
        amPmMarker("AM", "PM")
    }
private val minsRegex = """(\d+) mins?""".toRegex()
private val hrsRegex = """(\d+) hrs?""".toRegex()

/**
 * Parses time from facebook feed item/post detail in all the provided formats.
 * Supported languages are: en
 *
 * Returns the time we can be sure has already passed.
 * Just Now means the post was sent [0, 59] seconds ago
 * Seconds and milliseconds are stripped
 */
fun String.parsePostPublishedAt(
    timeZone: TimeZone,
    clock: Clock = Clock.System,
): Instant =
    run {
        val now = clock.now()

        // just now
        now
            .takeIf { this == "Just now" }
            ?.minus(59.seconds)
            // minutes
            ?: (
                minsRegex
                    .find(this)
                    ?.groups
                    ?.get(1)
                    ?.value
                    ?.toIntOrNull()
                    ?.let { now - it.minutes - 59.seconds }
            )
            // hours
            ?: (
                hrsRegex
                    .find(this)
                    ?.groups
                    ?.get(1)
                    ?.value
                    ?.toIntOrNull()
                    ?.let { now - it.hours - 59.minutes }
                    // make sure seconds are zeroed
                    ?.let { it - (it.epochSeconds % 3600).seconds }
            )?.also {
                println("---------- !!! WARNING !!! ----------")
                println("Using instant without a complete date information (minutes are missing)")
                println("This may lead to race conditions and some recent posts not being posted")
                println("-------------------------------------")
            }
            // yesterday
            ?: postPublishedAtFormatYesterday
                .parseOrNull(this)
                ?.also { components ->
                    val dateTime = now.toLocalDateTime(timeZone).date.minus(1, DateTimeUnit.DAY)
                    components.year = dateTime.year
                    components.month = dateTime.month
                    components.dayOfMonth = dateTime.dayOfMonth
                }?.toLocalDateTime()
                ?.toInstant(timeZone)
            // this year
            ?: postPublishedAtFormatThisYear
                .parseOrNull(this)
                ?.also { it.year = now.toLocalDateTime(timeZone).year }
                ?.toLocalDateTime()
                ?.toInstant(timeZone)
            // past years
            ?: postPublishedAtFormatPast
                .parseOrNull(this)
                ?.toLocalDateTime()
                ?.toInstant(timeZone)
            ?: throw IllegalArgumentException("Date & time '$this' cannot be parsed")
    }.stripSeconds()

private fun Instant.stripSeconds() = Instant.fromEpochSeconds((epochSeconds / 60) * 60)
