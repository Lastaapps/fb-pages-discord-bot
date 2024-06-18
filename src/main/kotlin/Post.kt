package cz.lastaapps

import kotlinx.datetime.Instant

data class Post(
    val id: String,
    val pageId: String,
    val publishedAt: Instant,
    val author: String,
    val description: String,
    val images: List<String>,
    val links: List<String>,
    val references: ReferencedPost?,
) {
    fun postLink(): String =
        "https://www.facebook.com/$pageId/$id"
}

data class ReferencedPost(
    val author: String,
    val description: String,
)