package cz.lastaapps.api.data.model

import cz.lastaapps.api.data.createdTimeToInstant
import cz.lastaapps.api.data.idToFacebookURL
import cz.lastaapps.api.domain.model.Place
import cz.lastaapps.api.domain.model.id.FBEventID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBPostID
import cz.lastaapps.api.domain.model.id.FBUserID
import cz.lastaapps.api.domain.model.token.AppAccessToken
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.UserAccessToken
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FBOAuthExchangeResponse(
    @SerialName("access_token")
    private val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
) {
    // only one of these function will be valid in the given context
    val userAccessToken get() = UserAccessToken(accessToken)
    val appAccessToken get() = AppAccessToken(accessToken)
}

@Serializable
data class FBMeResponse(
    @SerialName("id")
    private val id: String,
    @SerialName("name")
    val name: String,
) {
    val fbId get() = FBUserID(id.toULong())
}

@Serializable
data class FBPageInfoList(
    @SerialName("data")
    val data: List<FBPageInfo>,
)

@Serializable
data class FBPageInfo(
    @SerialName("id")
    private val id: String,
    @SerialName("name")
    val name: String,
) {
    val fbId get() = FBPageID(id.toULong())
}

@Serializable
data class FBManagedPages(
    @SerialName("data")
    val data: List<FBManagedPage>,
)

@Serializable
data class FBManagedPage(
    @SerialName("id")
    private val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("access_token")
    private val accessToken: String,
) {
    val fbId get() = FBPageID(id.toULong())
    val pageAccessToken get() = PageAccessToken(accessToken)
}

/**
 * https://developers.facebook.com/docs/graph-api/reference/v20.0/page/feed
 * "full_picture,id,is_hidden,is_published,message,place,status_type,is_expired,child_attachments,attachments"
 *
 *
 * This is hell lot of a spaghetti code that should NOT be in model.
 * But I did it anyway out of laziness, sorry.
 *
 * # Just text
 * simple, just take the message
 *
 * # Image, album, (video)
 * switch on media_type=photo
 * attachment.description contains text of the original post
 *
 * # Check in
 * type=map, media_type=link
 * info about location in the place field
 * take link from attachment url as a link to place
 * attachment is useless
 *
 * # Link
 * type=map, media_type=link
 * attachment.target.link is the proper redirect
 * attachment.target.description is the web textual preview
 * attachment.media.image.src is the web image preview
 *
 * # Event
 * type=event, media_type=event
 * the event id is stored in attachment/target/id
 * attachment.title is the event title
 */
@Serializable
data class FBPagePost(
    @SerialName("id")
    private val id: String,
    @SerialName("message")
    val message: String? = null,
    @SerialName("full_picture")
    private val fullPicture: String? = null,
    @SerialName("attachments")
    private val attachments: FBAttachment.Container? = null,
    @SerialName("place")
    val place: FBPlace? = null,
    @SerialName("is_hidden")
    private val isHidden: Boolean,
    @SerialName("is_published")
    private val isPublished: Boolean,
    @SerialName("is_expired")
    private val isExpired: Boolean,
    @SerialName("created_time")
    private val createdTime: String,
) {
    val createdAt: Instant = createdTime.createdTimeToInstant()

    val fbId get() = FBPostID(id)

    fun toURL() = id.idToFacebookURL()

    fun canBePublished() = !isHidden && isPublished && !isExpired

    @Serializable
    data class Container(
        @SerialName("data")
        val data: List<FBPagePost>,
    )

    fun attachments() =
        attachments?.data
            // there are two variants: "at the moment" and "right now"
            ?.filterNot { it.title?.startsWith("This content isn't available") == true }
            .orEmpty()
}

@Serializable
data class FBPlace(
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("location")
    val location: Location? = null,
) {
    fun toURL() = id?.idToFacebookURL()

    @Serializable
    data class Location(
        val city: String? = null,
        val country: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) {
        fun toDomain() = Place.Location(
            city = city,
            country = country,
            latitude = latitude,
            longitude = longitude,
        )
    }
}

/**
 * https://developers.facebook.com/docs/graph-api/reference/story-attachment
 */
@Serializable
data class FBAttachment(
    @SerialName("title")
    val title: String? = null,
    @SerialName("description")
    val description: String? = null,
    // Official: album, animated_image_autoplay, checkin, cover_photo, event, link,
    //           multiple, music, note, offer, photo, profile_media, status, video, video_autoplay
    // Found:    map, share
    @SerialName("type")
    val type: String,
    @SerialName("target")
    val target: Target? = null,
    // Official: photo, album, event, video
    // Found:    link
    @SerialName("media_type")
    val mediaType: String? = null,
    @SerialName("media")
    val media: Media? = null,
    @SerialName("subattachments")
    val subAttachments: Container? = null,
) {
    @Serializable
    data class Container(
        @SerialName("data")
        val data: List<FBAttachment>,
    )

    @Serializable
    data class Target(
        val id: String? = null,
        val url: String? = null,
    )

    @Serializable
    data class Media(
        val image: Image,
    )

    @Serializable
    data class Image(
        val src: String,
        val height: Int,
        val width: Int,
    )
}

@Serializable
data class FBEvent(
    @SerialName("id")
    private val id: String,
    @SerialName("cover")
    val coverPhoto: CoverPhoto? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("place")
    val place: FBPlace? = null,
    @SerialName("start_time")
    private val startTime: String? = null,
    @SerialName("end_time")
    private val endTime: String? = null,
    @SerialName("timezone")
    val timezone: TimeZone,
    @SerialName("type")
    val type: Type,
    @SerialName("is_online")
    val isOnline: Boolean,
    @SerialName("is_canceled")
    val isCanceled: Boolean,
    @SerialName("is_draft")
    val isDraft: Boolean,
) {
    @Serializable
    data class CoverPhoto(
        val id: String,
        val source: String,
    )

    @Suppress("unused")
    @Serializable
    enum class Type {
        @SerialName("private")
        PRIVATE,

        @SerialName("public")
        PUBLIC,

        @SerialName("group")
        GROUP,

        @SerialName("community")
        COMMUNITY,

        @SerialName("friends")
        FRIENDS,

        @SerialName("work_company")
        WORK_COMPANY,
    }

    val startAt = startTime?.createdTimeToInstant()
    val endAt = endTime?.createdTimeToInstant()

    val fbId get() = FBEventID(id)

    fun toURL() = id.idToFacebookURL()

    fun canBePublished() = !isCanceled && !isDraft && type != Type.PRIVATE
}
