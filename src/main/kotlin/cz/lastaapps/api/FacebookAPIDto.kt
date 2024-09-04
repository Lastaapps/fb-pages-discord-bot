package cz.lastaapps.api

import cz.lastaapps.common.decodeFacebookUrl
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

@Serializable
data class OAuthExchangeResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
)

@Serializable
data class MeResponse(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
)

@Serializable
data class PageInfo(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
)

@Serializable
data class ManagedPages(
    @SerialName("data")
    val data: List<ManagedPage>,
)

@Serializable
data class ManagedPage(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("access_token")
    val pageAccessToken: String,
)

/**
 * https://developers.facebook.com/docs/graph-api/reference/v20.0/page/feed
 * "full_picture,id,is_hidden,is_published,message,place,status_type,is_expired,child_attachments,attachments"
 *
 *
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
data class PagePost(
    @SerialName("id")
    val id: String,
    @SerialName("message")
    val message: String? = null,
    @SerialName("full_picture")
    private val fullPicture: String? = null,
    @SerialName("attachments")
    private val attachments: Attachment.Container? = null,
    @SerialName("place")
    val place: Place? = null,
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

    @Serializable
    data class Container(
        @SerialName("data")
        val data: List<PagePost>,
    )

    fun toURL() = id.idToFacebookURL()

    /** Returns a list of image URLs associated with the post */
    fun images(limit: Int = 5): List<String> {
        val imagesSet = mutableSetOf<String>()
        val imagesOrdered = mutableListOf<String>()

        fun addImage(src: String) {
            if ("fbcdn" in src && src !in imagesSet) {
                imagesSet += src
                imagesOrdered += src
            }
        }

        // this is the most important image of the whole post
        // disabled for now as it interferes with events
//        fullPicture?.let(::addImage)

        fun processAttachment(attachment: Attachment) {
            // to handle albums, where media type is not presented
            if (attachment.type == "photo") {
                attachment.media
                    ?.image
                    ?.src
                    ?.let(::addImage)
                return
            }

            when (attachment.mediaType) {
                "photo", "link" ->
                    if (attachment.type in listOf("photo", "link", "status", "share")) {
                        attachment.media
                            ?.image
                            ?.src
                            ?.let(::addImage)
                    }

                "album" -> attachment.subAttachments?.data?.forEach(::processAttachment)
            }
        }
        attachments?.data?.forEach(::processAttachment)
        return imagesOrdered.take(limit)
    }

    fun titlesAndDescriptions(): List<Pair<String?, String?>> {
        val list = mutableListOf<Pair<String?, String?>>(null to message)
        attachments?.data?.forEach {
            if (it.type !in listOf("event", "map")) {
                if (it.mediaType !in listOf("album")) {
                    list.add(it.title to it.description)
                }
            }
        }
        return list
            .filter { it.first != null || it.second != null }
            .distinct()
    }

    /** Returns a list of links associated with the post */
    fun links(): List<String> =
        attachments?.data?.mapNotNull { it ->
            it.target
                ?.url
                ?.takeIf(::isFBLink)
                ?.let(::decodeFacebookUrl)
        } ?: emptyList()

    /** Returns a list of event IDs associated with the post */
    fun eventIDs(): List<String> =
        attachments?.data?.mapNotNull {
            if (it.type == "event") {
                it.target?.id
            } else {
                null
            }
        } ?: emptyList()

    fun canBePublished() = !isHidden && isPublished && !isExpired
}

@Serializable
data class Place(
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
        val formattedLatitude
            get() =
                latitude?.let { coordinate ->
                    "%.6f%s".format(coordinate.absoluteValue, if (coordinate >= 0) "N" else "S")
                }
        val formattedLongitude
            get() =
                longitude?.let { coordinate ->
                    "%.6f%s".format(coordinate.absoluteValue, if (coordinate >= 0) "E" else "W")
                }
    }
}

/**
 * https://developers.facebook.com/docs/graph-api/reference/story-attachment
 */
@Serializable
data class Attachment(
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
        val data: List<Attachment>,
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
data class Event(
    @SerialName("id")
    val id: String,
    @SerialName("cover")
    val coverPhoto: CoverPhoto? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("place")
    val place: Place? = null,
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

    fun toURL() = id.idToFacebookURL()

    fun canBePublished() = !isCanceled && !isDraft && type != Type.PRIVATE
}
