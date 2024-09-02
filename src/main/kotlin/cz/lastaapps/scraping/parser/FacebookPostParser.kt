package cz.lastaapps.scraping.parser

import cz.lastaapps.scraping.model.Post
import cz.lastaapps.scraping.model.ReferencedPost
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseEventId
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseImages
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseLinks
import cz.lastaapps.scraping.parser.FacebookCommonParser.parsePublishedAt
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseReferencedPost
import it.skrape.core.htmlDocument
import kotlinx.datetime.TimeZone

object FacebookPostParser {
    fun parsePost(
        body: String,
        pageId: String,
        postId: String,
        timeZone: TimeZone,
    ): Post =
        htmlDocument(body) {
            findFirst("#root") {
                val postTextSection = findLast("footer").parent.children.first()

                val header = findFirst("header")
                val postedBy = header.findFirst("h3").text
                val description =
                    header.parent.children[1]
                        .wholeText.trimLines()

                var references: ReferencedPost? = null
                tryFindFirst("article") {
                    references = parseReferencedPost()
                }

                val publishedAt = parsePublishedAt(timeZone)

                val images = postTextSection.parseImages()
                val eventId = postTextSection.parseEventId()
                val links = parseLinks()

                Post(
                    id = postId,
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
}
