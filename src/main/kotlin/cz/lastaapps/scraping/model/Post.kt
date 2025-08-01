package cz.lastaapps.scraping.model

import kotlin.time.Instant

data class Post(
    val id: String,
    val pageId: String,
    val publishedAt: Instant,
    val author: String,
    val description: String,
    val images: List<String>,
    val links: List<String>,
    val eventId: String?,
    val references: ReferencedPost?,
) {
    fun postLink(): String = "https://www.facebook.com/$pageId/posts/$id"
}
