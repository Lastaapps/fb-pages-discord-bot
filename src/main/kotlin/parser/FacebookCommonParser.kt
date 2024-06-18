package cz.lastaapps.parser

import cz.lastaapps.model.ReferencedPost
import cz.lastaapps.parsePostPublishedAt
import io.ktor.http.decodeURLQueryComponent
import it.skrape.selects.DocElement
import kotlinx.datetime.Instant

object FacebookCommonParser {
    fun DocElement.parsePublishedAt(): Instant =
        findLast("footer") {
            findFirst("abbr").text
                .replaceSpaces(" ")
                .parsePostPublishedAt()
        }

    fun DocElement.parsePostID(): String =
        findLast("footer") {
            eachHref
                .first { href -> href.startsWith("/reactions/picker/") }
                .splitToSequence('?', '&')
                .drop(1)
                .first { it.startsWith("ft_id") }
                .removePrefix("ft_id=")
        }

    fun DocElement.parseReferencedPost(): ReferencedPost {
        // who posted the post, can be *name* posted with *name2*
        val postedBy = findFirst("header").text//.also(::println)
        val mainText = findFirst("p").text//.also(::println)

        return ReferencedPost(
            author = postedBy,
            description = mainText,
        )
    }

    fun DocElement.parseEventId(): String? =
        eachHref
            .firstOrNull { it.startsWith("/events/") }
            ?.removePrefix("/events/")
            ?.takeWhile { it.isDigit() }

    fun DocElement.parseImages(): List<String> {
        val images = mutableListOf<String>()
        tryFindAllAndCycle("img") {
            if (hasAttribute("src")) {
                images += attribute("src")
            }
        }
        return images
    }

    fun DocElement.parseLinks() : List<String>{
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
        return links
    }

    private fun decodeFacebookUrl(url: String): String =
        url
            .splitToSequence('&', '?')
            .first { it.startsWith("u=") }
            .removePrefix("u=")
            .decodeURLQueryComponent()
            .splitToSequence('&', '?')
            .filterNot { it.startsWith("fbclid=") }
            .let { parts ->
                parts.first() + (parts.drop(1).joinToString("&").takeUnless { it.isEmpty() }?.let { "?$it" } ?: "")
            }
}
