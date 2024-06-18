package cz.lastaapps

import cz.lastaapps.FacebookCommonParser.decodeFacebookUrl
import cz.lastaapps.FacebookCommonParser.parsePublishedAt
import cz.lastaapps.FacebookCommonParser.parseReferencedPost
import it.skrape.core.htmlDocument

object FacebookPostParser {
    fun parsePost(body: String, pageId: String, postId: String): Post =
        htmlDocument(body) {
            findFirst("#root") {

                val header = findFirst("header")
                val postedBy = header.text
                val description = header.parent.children[1].text

                var references: ReferencedPost? = null
                tryFindFirst("article") {
                    references = parseReferencedPost()
                }

                val publishedAt = parsePublishedAt()

                val images = mutableListOf<String>()
                findLast("footer").parent.children.first()
                    .tryFindAllAndCycle("img") {
                        if (hasAttribute("src")) {
                            images += attribute("src")
                        }
                    }

                val links = mutableListOf<String>()
                allElements.forEachApply {
                    if (hasAttribute("href")
                        && attribute("href").let { href ->
                            href.startsWith("https://l.facebook.com")
                                    || href.startsWith("https://lm.facebook.com")
                        }
                    ) {
                        links += decodeFacebookUrl( attribute("href"))
                    }
                }

                Post(
                    id = postId,
                    pageId = pageId,
                    publishedAt = publishedAt,
                    author = postedBy,
                    description = description,
                    images = images,
                    links = links,
                    references = references,
                )
            }
        }
}