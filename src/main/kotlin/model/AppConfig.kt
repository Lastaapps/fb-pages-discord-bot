package cz.lastaapps.model

import kotlinx.datetime.TimeZone
import kotlin.time.Duration

data class AppConfig(
    val debugMode: Boolean,
    val cookies: AppCookies,
    val dcToken: String,
    val dcChannelID: String,
    val pageIds: List<String>,
    val delay: Duration,
    // idk, somehow I managed that FB is using different timezone in different places
    val timeZoneFeed: TimeZone,
    val timeZonePost: TimeZone,
    val activeHoursRange: IntRange,
    val activeHoursTimezone: TimeZone,
) {
    init {
        // Yes, this is a bad practice, but I'm lazy to do otherwise
        check(dcToken.isNotBlank()) { "DC token cannot be blank" }
        check(dcChannelID.isNotBlank()) { "Channel id cannot be blank" }
        check(pageIds.all { it.isNotBlank() }) { "Page names cannot be blank" }
    }
}

data class AppCookies(
    val cUser: String,
    val xs: String,
    val mPageVoice: String,
) {
    init {
        check(cUser.isNotBlank()) { "c_user cannot be blank" }
        check(xs.isNotBlank()) { "x_s cannot be blank'" }
        check(mPageVoice.isNotBlank()) { "m_page_voice cannot be blank" }
    }
}
