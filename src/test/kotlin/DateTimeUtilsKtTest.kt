import cz.lastaapps.parsePostPublishedAt
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone.Companion.UTC

class DateTimeUtilsKtTest : StringSpec({
    val clock = object : Clock{
        override fun now(): Instant {
            return LocalDateTime(2024, Month.JUNE, 19, 16, 42, 36, 123).toInstant(UTC)
        }
    }
    "parseFacebookTime" {
        forAll(
            row("Just now", LocalDateTime(2024, Month.JUNE, 19, 16, 42)),
            row("1 min", LocalDateTime(2024, Month.JUNE, 19, 16, 41)),
            row("18 mins", LocalDateTime(2024, Month.JUNE, 19, 16, 24)),
            row("1 hr", LocalDateTime(2024, Month.JUNE, 19, 15, 0)),
            row("18 hrs", LocalDateTime(2024, Month.JUNE, 18, 22, 0)),
            row("Yesterday at 9:17 AM", LocalDateTime(2024, Month.JUNE, 18, 9, 17)),
            row("May 27 at 6:13 PM", LocalDateTime(2024, Month.MAY, 27, 18, 13 )),
            row("May 7 at 6:13 PM", LocalDateTime(2024, Month.MAY, 7, 18, 13 )),
            row("November 19, 2023 at 9:00 AM", LocalDateTime(2023, Month.NOVEMBER, 19, 9, 0 )),
            row("June 6, 2023 at 3:48 PM", LocalDateTime(2023, Month.JUNE, 6, 15, 48))
        ) { name, res ->
            name.parsePostPublishedAt(UTC, clock) shouldBeEqual res.toInstant(UTC)
        }
    }
})