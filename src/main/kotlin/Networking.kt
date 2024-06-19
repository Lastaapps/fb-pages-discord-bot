package cz.lastaapps

import cz.lastaapps.model.AppCookies
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.cookie
import io.ktor.client.request.get

fun createClient(cookies: AppCookies = loadConfig().cookies): HttpClient =
    HttpClient {
        BrowserUserAgent()
        install(DefaultRequest) {
            cookie(name = "c_user", cookies.cUser)
            cookie(name = "xs", cookies.xs)
            cookie(name = "m_page_voice", cookies.mPageVoice)
        }
    }

suspend fun downloadFeed(
    client: HttpClient,
    pageNameOrId: String,
): String {
    val url = "https://mbasic.facebook.com/$pageNameOrId?v=timeline"
    return client.get(url).body<String>()
}

suspend fun downloadPost(
    client: HttpClient,
    pageId: String,
    postId: String,
): String {
    val url = "https://mbasic.facebook.com/story.php?story_fbid=$postId&id=$pageId"
    return client.get(url).body<String>()
}

suspend fun downloadEvent(
    client: HttpClient,
    eventId: String,
): String {
    val url = "https://mbasic.facebook.com/events/$eventId"
    return client.get(url).body<String>()
}
