package cz.lastaapps.api.data.provider

import arrow.core.raise.either
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.api.FBDataAPI
import cz.lastaapps.api.data.model.FBAttachment
import cz.lastaapps.api.data.model.FBPagePost
import cz.lastaapps.api.data.util.extractLinks
import cz.lastaapps.api.data.util.isFBEventLink
import cz.lastaapps.api.data.util.isFBLink
import cz.lastaapps.api.data.util.isFBRedirectLink
import cz.lastaapps.api.data.util.toUrlOrNull
import cz.lastaapps.api.domain.LazyProvider
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.Post
import cz.lastaapps.api.domain.model.ResolvedLink
import cz.lastaapps.api.domain.model.id.FBEventID
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBPostID
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.common.decodeFacebookUrl
import io.ktor.http.Url

class PostProvider(
    private val api: FBDataAPI,
    private val linkResolver: LinkResolver,
    private val locationConverter: LocationConverter,
) {
    suspend fun loadPagePosts(
        pageID: FBPageID,
        pageAccessToken: PageAccessToken,
        limit: UInt? = null,
    ): Outcome<List<LazyProvider<FBPostID, Outcome<Post>>>> = api.loadPagePosts(pageID, pageAccessToken, limit = limit)
        .map { posts ->
            posts
                .filter { it.canBePublished() }
                .map { post -> LazyProvider(post.fbId) { post.toDomain() } }
        }

    private suspend fun FBPagePost.toDomain() = either {
        suspend fun resolve(url: Url) = linkResolver.resolve(url).bind()

        val resolvedLink = resolve(toURL())
        val sections = sections()
        val images = images().map { Url(it) }

        val rawLinks = rawLinksInText().map { resolve(it) }
        val linksInAttachments = attachmentLinks().map { resolve(it) }
        val linksInText = linksInText(rawLinks, linksInAttachments)
        val accessibleEventIds = accessibleEventIDs()
        val inaccessibleEventIds = inaccessibleEventIDs(rawLinks, accessibleEventIds)

        val place = place?.let { locationConverter.convertPlace(it) }

        Post(
            id = fbId,
            createdAt = createdAt,
            link = resolvedLink,
            message = message,
            sections = sections,
            images = images,
            linksInText = linksInText,
            linksInAttachments = linksInAttachments,
            accessibleEventIds = accessibleEventIds,
            inaccessibleEventIds = inaccessibleEventIds,
            place = place,
        )
    }

    /** Returns a list of image URLs associated with the post */
    private fun FBPagePost.images(limit: Int = 5): List<String> {
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

        fun processAttachment(attachment: FBAttachment) {
            // to handle albums, where media type is not presented
            if (attachment.type in listOf("photo", "cover_photo")) {
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
        attachments().forEach(::processAttachment)
        return imagesOrdered.take(limit)
    }

    private fun FBPagePost.rawLinksInText(): List<Url> =
        message?.extractLinks().orEmpty()
            // the regex also matches links that are in parentheses
            // "(https://www.example.com)"
            .map { it.dropWhile { it == '(' } }
            .map { it.dropLastWhile { it == ')' } }
            .mapNotNull { it.toUrlOrNull(log) }

    /** Returns a list of links associated with the post */
    private fun FBPagePost.attachmentLinks(): List<Url> =
        attachments().mapNotNull { it ->
            it.target
                ?.url
                ?.takeIf(::isFBRedirectLink)
                ?.let(::decodeFacebookUrl)
        }
            .mapNotNull { it.toUrlOrNull(log) }

    /**
     * Returns links in the text of the post
     * excluding links to Facebook events
     * that should be handled separately
     */
    private fun FBPagePost.linksInText(
        rawLinks: List<ResolvedLink>,
        linksInAttachments: List<ResolvedLink>,
    ): List<ResolvedLink> =
        rawLinks
            .filterNot { isFBLink(it.link.toString()) }
            .filterNot { isFBEventLink(it.link.toString()) }
            .filterNot { it in linksInAttachments }

    /** Returns a list of event IDs associated with the post.
     * The token used should have rights to access the events details using API
     */
    private fun FBPagePost.accessibleEventIDs(): List<FBEventID> =
        attachments().mapNotNull {
            if (it.type == "event") {
                it.target?.id?.let(::FBEventID)
            } else {
                null
            }
        }

    /** Returns a list of event IDs destiled from the posts content.
     * This happens when a page shares an event by
     * adding a link to the event into the post text.
     * We also have to make sure that in case we have access to the event,
     * we do not return it twice, therefore we have to filter out event IDs
     * from the attachment section.
     * These should be previewed using Discord automatically
     * by just posting the link in a separate message
     */
    private fun inaccessibleEventIDs(
        rawLinks: List<ResolvedLink>,
        eventIdsInAttachments: List<FBEventID>,
    ): List<FBEventID> =
        rawLinks
            .filter { isFBEventLink(it.link.toString()) }
            .mapNotNull {
                it.link.segments.lastOrNull()?.trim()?.let(::FBEventID)
            }
            .filterNot { it in eventIdsInAttachments }

    private fun FBPagePost.sections(): List<Post.PostSection> {
        val list = mutableListOf(Post.PostSection(null, message))

        attachments().forEach {
            if (it.type !in listOf("event", "map")) {
                if (it.mediaType !in listOf("album")) {
                    list.add(Post.PostSection(it.title, it.description))
                }
            }
        }
        return list
            .filter { it.title != null || it.message != null }
            .distinct()
    }

    companion object {
        private val log = Logger.withTag("PostProvider")
    }
}
