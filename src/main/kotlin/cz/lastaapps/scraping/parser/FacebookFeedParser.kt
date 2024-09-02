package cz.lastaapps.scraping.parser

import arrow.core.Either
import cz.lastaapps.scraping.model.Post
import cz.lastaapps.scraping.model.ReferencedPost
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseEventId
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseImages
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseLinks
import cz.lastaapps.scraping.parser.FacebookCommonParser.parsePostID
import cz.lastaapps.scraping.parser.FacebookCommonParser.parsePublishedAt
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseReferencedPost
import it.skrape.core.htmlDocument
import it.skrape.selects.DocElement
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC

object FacebookFeedParser {
    /**
     * Parses page content for the overview screen
     * The post descriptions may be partial only
     */
    fun parsePostsFromFeed(
        body: String,
        timeZone: TimeZone = UTC,
    ): List<Post> {
        val posts = mutableListOf<Post>()
        htmlDocument(body).apply {
            // parses page id from the page profile image
            val pageId =
                findAll("#m-timeline-cover-section a")
                    .first { it.hasAttribute("href") && it.attribute("href").startsWith("/photo.php") }
                    .attribute("href")
                    .split("?", "&")
                    .first { it: String -> it.startsWith("id=") }
                    .removePrefix("id=")

            findFirst("div#tlFeed") {
                findFirst("section").children.forEachApply {
                    Either.catch {
                        posts += parseFeedPost(pageId, timeZone)
                    }.onLeft { it.printStackTrace() }
                }
            }
        }

        return posts
    }

    private fun DocElement.parseFeedPost(
        pageId: String,
        timeZone: TimeZone,
    ): Post {
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
        val publishedAt = parsePublishedAt(timeZone)

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
