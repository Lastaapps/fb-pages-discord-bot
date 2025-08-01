package cz.lastaapps.scraping.parser

import cz.lastaapps.common.decodeFacebookUrl
import cz.lastaapps.scraping.model.ReferencedPost
import cz.lastaapps.scraping.parsePostPublishedAt
import it.skrape.selects.DocElement
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

object FacebookCommonParser {
    fun DocElement.parsePublishedAt(timeZone: TimeZone): Instant =
        findLast("footer") {
            findFirst("abbr").text
                .replaceSpaces(" ")
                .parsePostPublishedAt(timeZone)
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
        val postedBy = findFirst("header").text // .also(::println)
        val mainText = findFirst("p").text // .also(::println)

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

    fun DocElement.parseLinks(): List<String> {
        val links = mutableListOf<String>()
        allElements.forEachApply {
            if (hasAttribute("href") &&
                attribute("href").let { href ->
                    href.startsWith("https://l.facebook.com") ||
                        href.startsWith("https://lm.facebook.com")
                }
            ) {
                links += decodeFacebookUrl(attribute("href"))
            }
        }
        return links
    }
}
