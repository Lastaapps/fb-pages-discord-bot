package cz.lastaapps

import cz.lastaapps.model.AppConfig
import cz.lastaapps.model.AppCookies
import kotlinx.coroutines.runBlocking


fun main(): Unit = runBlocking {
    dropPrivileges()

    val config = loadConfig()
    val client = createClient(config.cookies)

    println(config)
}

private fun dropPrivileges(){
    // TODO
}

fun loadConfig(): AppConfig = AppConfig(
    AppCookies(
        cUser = System.getenv("FACEBOOK_COOKIE_c_user"),
        xs = System.getenv("FACEBOOK_COOKIE_x_s"),
        mPageVoice = System.getenv("FACEBOOK_COOKIE_m__page_voice"),
    ),
    dcToken = System.getenv("FACEBOOK_DC_TOKEN"),
    dcChannelID = System.getenv("FACEBOOK_DC_CHANNEL"),
    pageIds = System.getenv("FACEBOOK_PAGES").split(","),
)
