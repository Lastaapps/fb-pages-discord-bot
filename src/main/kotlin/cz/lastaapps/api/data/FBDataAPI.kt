package cz.lastaapps.api.data

import arrow.core.right
import co.touchlab.kermit.Logger
import cz.lastaapps.api.API_VERSION
import cz.lastaapps.api.data.model.Event
import cz.lastaapps.api.data.model.PageInfoList
import cz.lastaapps.api.data.model.PagePost
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Page
import cz.lastaapps.api.domain.model.id.FBEventID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.token.AppAccessToken
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
        log.d { "Loading page posts ${pageID.id}" }
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
        eventID: FBEventID,
        pageAccessToken: PageAccessToken,
    ): Event {
        log.d { "Loading event ${eventID.id}" }
        return client
            .get("/$API_VERSION/${eventID.id}") {
                parameter("access_token", pageAccessToken.token)
                parameter(
                    "fields",
                    "id,cover,name,description,type,place,start_time,end_time,timezone,is_canceled,is_draft,is_online,photos,created_time",
                )
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<Event>()
            }
    }

    suspend fun searchPages(
        appAccessToken: AppAccessToken,
        name: String,
    ): Outcome<List<Page>> {
        log.d { "Searching for $name" }
        return client
            .get("/$API_VERSION/pages/search") {
                parameter("q", name)
                parameter("access_token", appAccessToken.token)
                parameter(
                    "fields",
                    "id,name",
                )
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<PageInfoList>().data
            }
            .map { Page(FBPageID(it.fbId.toULong()), it.name) }
            .right()
    }
}
