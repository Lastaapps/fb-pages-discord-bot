package cz.lastaapps

import arrow.core.Either
import it.skrape.core.htmlDocument
import it.skrape.selects.DocElement
import kotlinx.datetime.Instant

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
        val mainText = findFirst("p").text//.also(::println)

        // if the article references another article, we load it here
        var references: ReferencedPost? = null
        tryFindFirst("article") {
            references = parseReferencedPost()
        }

        val (id, publishedAt) = parsePostIDAndPublishTime()

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
                links += attribute("href")
            }
        }

        return Post(
            id = id,
            publishedAt = publishedAt,
            pageId = pageId,
            author = postedBy,
            description = mainText,
            images = images,
            links = links,
            references = references,
        )
    }

    private fun DocElement.parsePostIDAndPublishTime(): Pair<String, Instant> =
        findLast("footer") {
            val id = eachHref
                .first { href -> href.startsWith("/reactions/picker/") }
                .splitToSequence('?', '&')
                .drop(1)
                .first { it.startsWith("ft_id") }
                .removePrefix("ft_id=")

            val publishedAt = findFirst("abbr").text
                .replaceSpaces(" ")
                .parseFacebookTime()

            Pair(
                id,
                publishedAt,
            )
        }

    private fun DocElement.parseReferencedPost(): ReferencedPost {
        // who posted the post, can be *name* posted with *name2*
        val postedBy = findFirst("header").text//.also(::println)
        val mainText = findFirst("p").text//.also(::println)

        return ReferencedPost(
            author = postedBy,
            description = mainText,
        )
    }
}