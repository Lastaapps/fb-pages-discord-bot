package cz.lastaapps.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class DataAPI(
    private val client: HttpClient,
) {
    private val log = Logger.withTag("DataAPI")

    suspend fun loadPagePosts(
        pageID: String,
        pageAccessToken: String,
    ): List<PagePost> {
        log.d { "Loading page posts $pageID" }
        return client
            .get("/${API_VERSION}/$pageID/feed") {
                parameter("access_token", pageAccessToken)
                parameter(
                    "fields",
                    "id,message,full_picture,place,is_hidden,is_published,is_expired,created_time,attachments{title,description,target,type,media,media_type,subattachments}",
                )
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<PagePost.Container>().data
            }
    }

    suspend fun loadEventData(
        eventID: String,
        pageAccessToken: String,
    ): Event {
        log.d { "Loading event $eventID" }
        return client
            .get("/${API_VERSION}/$eventID") {
                parameter("access_token", pageAccessToken)
                parameter(
                    "fields",
                    "id,cover,name,description,type,place,start_time,end_time,timezone,is_canceled,is_draft,is_online,photos,created_time",
                )
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<Event>()
            }
    }
}
