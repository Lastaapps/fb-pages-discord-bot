package cz.lastaapps.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

const val API_VERSION = "v20.0"

const val STATE = "12345" // no needed in our case as we do not use an online session


fun main() = runBlocking {
    val config = AppConfig.fromEnv()
    val client = createHttpClient()

    when (4) {
        1 -> launchOAuth(config.facebook)
        2 -> exchangeOAuth(config.facebook, client, STATE)
        3 -> grantAccess(client, USER_ACCESS_TOKEN)
        4 -> loadPageData(client, PAGE_ID, PAGE_ACCESS_TOKEN)
    }
}

/**
 * https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow
 * https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived
 * https://developers.facebook.com/docs/facebook-login/facebook-login-for-business
 */
fun launchOAuth(config: AppConfig.Facebook) {
    println(
        "https://www.facebook.com/$API_VERSION/dialog/oauth?" +
            "client_id=${config.appID}" +
            "&redirect_uri=${config.redirectURL.encodeURLParameter()}" +
            "&config_id=${config.configID}" +
            "&state=${STATE.encodeURLParameter()}",
    )
}

/**
 * https://developers.facebook.com/tools/explorer
 */
suspend fun exchangeOAuth(config: AppConfig.Facebook, client: HttpClient, originalState: String) {
    val parts = URL_WITH_CODE.splitToSequence("?", "&", "#")
        .drop(1)
        .map { it.decodeURLQueryComponent() }
        .map { it.split("=", limit = 2) }
        .filterNot { it.first() == "_" }
        .associate { (a, b) -> a to b }
    println(parts)
    val code = parts["code"]!!
    val state = parts["state"]!!
    check(state == originalState) { "Ha, pojeb" }

    val response = client.get(
        "/${API_VERSION}/oauth/access_token?" +
            "client_id=${config.appID}" +
            "&redirect_uri=${config.appID.encodeURLParameter()}" +
            "&client_secret=${config.appID.encodeURLParameter()}" +
            "&code=${code.encodeURLParameter()}",
    )
    println("Status code: ${response.status}")
    println(response.body<OAuthExchangeResponse>())
}

suspend fun grantAccess(client: HttpClient, userAccessToken: String) {
    val userId = client.get("/${API_VERSION}/me") {
        parameter("fields", "id,name")
        parameter("access_token", userAccessToken)
    }.let { response ->
        println("Status code: ${response.status}")
        val data = response.body<MeResponse>()
        println(data)
        data.id
    }
    val accessTokens = client.get(
        "/${API_VERSION}/${userId}/accounts",
    ) {
        parameter("access_token", userAccessToken)
    }.let { response ->
        println("Status code: ${response.status}")
        val data = response.body<ManagedPages>()
        println(data)
        data.data
    }
}

suspend fun loadPageData(client: HttpClient, pageID: String, pageAccessToken: String) {
    client.get("/${API_VERSION}/$pageID/feed") {
        parameter("access_token", pageAccessToken)
        parameter(
            "fields",
            "id,message,full_picture,place,is_hidden,is_published,is_expired,created_time,attachments{title,description,target,type,media,media_type,subattachments}",
        )
    }.let { response ->
        println("Status code: ${response.status}")
        val data = response.body<PagePost.Container>()
        data.data.forEach {
            println("-".repeat(80))
            println(it)
            println(it.images())
            println(it.titlesAndDescriptions())
            println(it.links())
            println(it.eventIDs())
            println(it.canBePublished())
            println("-".repeat(80))
            it.eventIDs().forEach { eventID ->
                loadEventData(client, eventID, pageAccessToken)
            }
            println("-".repeat(80))
        }
    }
}

suspend fun loadEventData(client: HttpClient, eventID: String, pageAccessToken: String) {
    client.get("/${API_VERSION}/$eventID") {
        parameter("access_token", pageAccessToken)
        parameter(
            "fields",
            "id,cover,name,description,type,place,start_time,end_time,timezone,is_canceled,is_draft,is_online,photos,created_time",
        )
    }.let { response ->
        println("Status code: ${response.status}")
        val data = response.body<Event>()
        println(data)
    }
}

private fun createHttpClient() = HttpClient {
    install(Logging) {
        level = LogLevel.INFO
    }
    install(DefaultRequest) {
        url(
            scheme = "https",
            host = "graph.facebook.com",
        )
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            },
        )
    }
}
