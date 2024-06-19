package cz.lastaapps

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

private val CET = TimeZone.of("Europe/Prague")

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

fun String.parsePostPublishedAt(
    timeZone: TimeZone = CET,
    clock: Clock = Clock.System,
): Instant =
    run {
        val yearNow = clock.now().toLocalDateTime(timeZone).year
        val now =
            clock.now()
                // strip seconds
                .let { it - (it.epochSeconds % 60).seconds }
                // strip milliseconds
                .let { Instant.fromEpochSeconds(it.epochSeconds) }

        // just now
        now.takeIf { this == "Just now" }
        // minutes
            ?: (
                minsRegex.find(this)?.groups?.get(1)?.value?.toIntOrNull()
                    ?.let { now - it.minutes }
                )
            // hours
            ?: (
                hrsRegex.find(this)?.groups?.get(1)?.value?.toIntOrNull()
                    ?.let { now - it.hours }
                    // make sure seconds are zeroed
                    ?.let { it - (it.epochSeconds % 3600).seconds }
                )
                ?.also {
                    println("---------- !!! WARNING !!! ----------")
                    println("Using instant without a complete date information (minutes are missing)")
                    println("This may lead to race conditions and some recent posts not being posted")
                    println("---------- !!! WARNING !!! ----------")
                }
            // yesterday
            ?: postPublishedAtFormatYesterday.parseOrNull(this)
                ?.also { components ->
                    val dateTime = now.toLocalDateTime(timeZone).date.minus(1, DateTimeUnit.DAY)
                    components.year = dateTime.year
                    components.month = dateTime.month
                    components.dayOfMonth = dateTime.dayOfMonth
                }
                ?.toLocalDateTime()
                ?.toInstant(timeZone)
            // this year
            ?: postPublishedAtFormatThisYear.parseOrNull(this)
                ?.also { it.year = yearNow }
                ?.toLocalDateTime()
                ?.toInstant(timeZone)
            // past years
            ?: postPublishedAtFormatPast.parseOrNull(this)
                ?.toLocalDateTime()
                ?.toInstant(timeZone)
            ?: throw IllegalArgumentException("Date & time '$this' cannot be parsed")
    }
