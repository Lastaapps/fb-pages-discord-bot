package cz.lastaapps.api.data

import co.touchlab.kermit.Logger
import cz.lastaapps.api.API_VERSION
import cz.lastaapps.api.data.model.Event
import cz.lastaapps.api.data.model.PagePost
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class FBDataAPI(
    private val client: HttpClient,
) {
    private val log = Logger.withTag("DataAPI")

    suspend fun loadPagePosts(
        pageID: FBPageID,
        pageAccessToken: PageAccessToken,
    ): List<PagePost> {
        log.d { "Loading page posts $pageID" }
        return client
            .get("/$API_VERSION/${pageID.id}/feed") {
                parameter("access_token", pageAccessToken.token)
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
        pageAccessToken: PageAccessToken,
    ): Event {
        log.d { "Loading event $eventID" }
        return client
            .get("/$API_VERSION/$eventID") {
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
