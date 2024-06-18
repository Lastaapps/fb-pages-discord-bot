package cz.lastaapps

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking


fun main(): Unit = runBlocking {
    val client = createClient()
}

fun createClient(): HttpClient = HttpClient {
    BrowserUserAgent()
    install(DefaultRequest) {
        //TODO cookies
    }
}

suspend fun downloadFeed(client: HttpClient, pageNameOrId: String): String {
    val url = "https://mbasic.facebook.com/$pageNameOrId?v=timeline"
    return client.get(url).body<String>()
}

suspend fun downloadPost(client: HttpClient, pageId: String, postId: String): String {
    val url = "https://mbasic.facebook.com/story.php?story_fbid=$postId&id=$pageId"
    return client.get(url).body<String>()
}

