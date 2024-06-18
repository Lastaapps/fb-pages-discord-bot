package cz.lastaapps.parser

import cz.lastaapps.model.Post
import cz.lastaapps.model.ReferencedPost
import cz.lastaapps.parser.FacebookCommonParser.parseEventId
import cz.lastaapps.parser.FacebookCommonParser.parseImages
import cz.lastaapps.parser.FacebookCommonParser.parseLinks
import cz.lastaapps.parser.FacebookCommonParser.parsePublishedAt
import cz.lastaapps.parser.FacebookCommonParser.parseReferencedPost
import it.skrape.core.htmlDocument

object FacebookPostParser {
    fun parsePost(body: String, pageId: String, postId: String): Post =
        htmlDocument(body) {
            findFirst("#root") {
                val postTextSection = findLast("footer").parent.children.first()

                val header = findFirst("header")
                val postedBy = header.text
                val description = header.parent.children[1]
                    .wholeText.trimLines()

                var references: ReferencedPost? = null
                tryFindFirst("article") {
                    references = parseReferencedPost()
                }

                val publishedAt = parsePublishedAt()

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