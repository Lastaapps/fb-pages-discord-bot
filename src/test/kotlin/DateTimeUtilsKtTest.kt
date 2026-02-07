import cz.lastaapps.scraping.parsePostPublishedAt
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.equals.shouldBeEqual
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant

class DateTimeUtilsKtTest :
    StringSpec(
        {
            val clock =
                object : Clock {
                    override fun now(): Instant = LocalDateTime(2024, Month.JUNE, 19, 16, 42, 36, 123).toInstant(UTC)
                }
            "parseFacebookTime" {
                val gmt1 = TimeZone.of("GMT+1")
                forAll(
                    row(UTC, "Just now", LocalDateTime(2024, Month.JUNE, 19, 16, 41)),
                    row(UTC, "1 min", LocalDateTime(2024, Month.JUNE, 19, 16, 40)),
                    row(UTC, "18 mins", LocalDateTime(2024, Month.JUNE, 19, 16, 23)),
                    row(UTC, "1 hr", LocalDateTime(2024, Month.JUNE, 19, 14, 0)),
                    row(UTC, "18 hrs", LocalDateTime(2024, Month.JUNE, 18, 21, 0)),
                    row(UTC, "Yesterday at 9:17 AM", LocalDateTime(2024, Month.JUNE, 18, 9, 17)),
                    row(UTC, "May 27 at 6:13 PM", LocalDateTime(2024, Month.MAY, 27, 18, 13)),
                    row(UTC, "May 7 at 6:13 PM", LocalDateTime(2024, Month.MAY, 7, 18, 13)),
                    row(UTC, "November 19, 2023 at 9:00 AM", LocalDateTime(2023, Month.NOVEMBER, 19, 9, 0)),
                    row(UTC, "June 6, 2023 at 3:48 PM", LocalDateTime(2023, Month.JUNE, 6, 15, 48)),
                    row(gmt1, "Just now", LocalDateTime(2024, Month.JUNE, 19, 16, 41)),
                    row(gmt1, "1 min", LocalDateTime(2024, Month.JUNE, 19, 16, 40)),
                    row(gmt1, "18 mins", LocalDateTime(2024, Month.JUNE, 19, 16, 23)),
                    row(gmt1, "1 hr", LocalDateTime(2024, Month.JUNE, 19, 14, 0)),
                    row(gmt1, "18 hrs", LocalDateTime(2024, Month.JUNE, 18, 21, 0)),
                    row(gmt1, "Yesterday at 9:17 AM", LocalDateTime(2024, Month.JUNE, 18, 8, 17)),
                    row(gmt1, "May 27 at 6:13 PM", LocalDateTime(2024, Month.MAY, 27, 17, 13)),
                    row(gmt1, "May 7 at 6:13 PM", LocalDateTime(2024, Month.MAY, 7, 17, 13)),
                    row(gmt1, "November 19, 2023 at 9:00 AM", LocalDateTime(2023, Month.NOVEMBER, 19, 8, 0)),
                    row(gmt1, "June 6, 2023 at 3:48 PM", LocalDateTime(2023, Month.JUNE, 6, 14, 48)),
                ) { timeZone, name, res ->
                    name.parsePostPublishedAt(timeZone, clock) shouldBeEqual res.toInstant(UTC)
                }
            }
        },
    )
