package cz.lastaapps.api.domain.model

import io.ktor.http.Url

@JvmInline
value class ResolvedLink(
    val link: Url,
) {
    override fun toString(): String = link.toString()
}
