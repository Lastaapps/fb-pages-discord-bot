package cz.lastaapps.api.domain.model

import cz.lastaapps.api.domain.model.id.FBEventID
import cz.lastaapps.api.domain.model.id.FBPostID
import io.ktor.http.Url
import kotlinx.datetime.Instant

/**
 * @param link URL of the post on Facebook that is already converted from ID
 */
data class Post(
    val id: FBPostID,
    val createdAt: Instant,
    val message: String?,
    val link: ResolvedLink,
    val sections: List<PostSection>,
    val images: List<Url>,
    val linksInText: List<ResolvedLink>,
    val linksInAttachments: List<ResolvedLink>,
    val accessibleEventIds: List<FBEventID>,
    val inaccessibleEventIds: List<FBEventID>,
    val place: Place?,
) {
    data class PostSection(
        val title: String?,
        val message: String?,
    )
}
