import cz.lastaapps.parsePostPublishedAt
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant

class DateTimeUtilsKtTest : StringSpec({
    "parseFacebookTime" {
        forAll(
            row("May 27 at 6:13 PM", LocalDateTime(2024, Month.MAY, 27, 18, 13 )),
            row("May 7 at 6:13 PM", LocalDateTime(2024, Month.MAY, 7, 18, 13 )),
//            row("November 19, 2023 at 9:00 AM", LocalDateTime(2023, Month.NOVEMBER, 19, 9, 0 )),
        ) { name, res ->
            name.parsePostPublishedAt(UTC, yearNow = 2024) shouldBeEqual res.toInstant(UTC)
        }
    }
})