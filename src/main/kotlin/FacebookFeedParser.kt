package cz.lastaapps

import arrow.core.Either
import cz.lastaapps.FacebookCommonParser.decodeFacebookUrl
import cz.lastaapps.FacebookCommonParser.parsePostID
import cz.lastaapps.FacebookCommonParser.parsePublishedAt
import cz.lastaapps.FacebookCommonParser.parseReferencedPost
import it.skrape.core.htmlDocument
import it.skrape.selects.DocElement

object FacebookFeedParser {

    /**
     * Parses page content for the overview screen
     * The post descriptions may be partial only
     */
    fun parsePostsFromFeed(body: String): List<Post> {
        val posts = mutableListOf<Post>()
        htmlDocument(body).apply{
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
        // who posted the post, can be *name* posted with *name2*
        val postedBy = findFirst("header").text//.also(::println)
        val description = findFirst("p").text//.also(::println)

        // if the article references another article, we load it here
        var references: ReferencedPost? = null
        tryFindFirst("article") {
            references = parseReferencedPost()
        }

        val id = parsePostID()
        val publishedAt = parsePublishedAt()

        val images = mutableListOf<String>()
        tryFindAllAndCycle(".story_body_container img") {
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

        return Post(
            id = id,
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