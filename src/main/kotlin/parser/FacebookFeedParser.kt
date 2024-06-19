package cz.lastaapps.parser

import arrow.core.Either
import cz.lastaapps.model.Post
import cz.lastaapps.model.ReferencedPost
import cz.lastaapps.parser.FacebookCommonParser.parseEventId
import cz.lastaapps.parser.FacebookCommonParser.parseImages
import cz.lastaapps.parser.FacebookCommonParser.parseLinks
import cz.lastaapps.parser.FacebookCommonParser.parsePostID
import cz.lastaapps.parser.FacebookCommonParser.parsePublishedAt
import cz.lastaapps.parser.FacebookCommonParser.parseReferencedPost
import it.skrape.core.htmlDocument
import it.skrape.selects.DocElement

object FacebookFeedParser {

    /**
     * Parses page content for the overview screen
     * The post descriptions may be partial only
     */
    fun parsePostsFromFeed(body: String): List<Post> {
        val posts = mutableListOf<Post>()
        htmlDocument(body).apply {
            // parses page id from the page profile image
            val pageId = findFirst("#profile_cover_photo_container a") {
                attribute("href")
                    .split("?", "&")
                    .first { it: String -> it.startsWith("id=") }
                    .removePrefix("id=")
            }

            findFirst("div#tlFeed") {
                findFirst("section").children.forEachApply {
                    Either.catch {
                        posts += parseFeedPost(pageId)
                    }.onLeft { it.printStackTrace() }
                }
            }
        }

        return posts
    }

    private fun DocElement.parseFeedPost(pageId: String): Post {
        val postTextSection = findFirst(".story_body_container")

        // who posted the post, can be *name* posted with *name2*
        val postedBy = findFirst("header").text
        val description = tryFindFirst("p") { wholeText.trimLines() } ?: ""

        // if the article references another article, we load it here
        var references: ReferencedPost? = null
        tryFindFirst("article") {
            references = parseReferencedPost()
        }

        val id = parsePostID()
        val publishedAt = parsePublishedAt()

        val images = postTextSection.parseImages()
        val eventId = postTextSection.parseEventId()
        val links = parseLinks()

        return Post(
            id = id,
            pageId = pageId,
            publishedAt = publishedAt,
            author = postedBy,
            description = description,
            images = images,
            links = links,
            eventId = eventId,
            references = references,
        )
    }
}