package cz.lastaapps

import io.ktor.http.decodeURLQueryComponent
import it.skrape.selects.DocElement
import kotlinx.datetime.Instant

object FacebookCommonParser {
    fun DocElement.parsePublishedAt(): Instant =
        findLast("footer") {
            findFirst("abbr").text
                .replaceSpaces(" ")
                .parseFacebookTime()
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

    fun decodeFacebookUrl(url: String): String =
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
