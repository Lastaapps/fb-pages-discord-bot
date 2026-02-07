package cz.lastaapps.api.data.provider

import arrow.core.right
import co.touchlab.kermit.Logger
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.error.catchingNetwork
import cz.lastaapps.api.domain.error.e
import cz.lastaapps.api.domain.model.ResolvedLink
import io.ktor.client.HttpClient
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.head
import io.ktor.http.Url
import io.ktor.http.decodeURLPart

class LinkResolver(
    client: HttpClient = HttpClient(),
) {
    private val client =
        client.config {
            followRedirects = true
            BrowserUserAgent()
            install(Logging) {
                level = LogLevel.NONE
            }
        }

    suspend fun resolve(link: Url): Outcome<ResolvedLink> =
        catchingNetwork {
            if (link.host.contains("facebook") || link.host.contains("fb")) {
                // yes, this is a potential security risk - remote url execution or something
                val response = client.head(link)
                val newLink =
                    response.call.request.url
                        .let(::removeLogin)

                if (newLink != link) {
                    log.v { "Resolved \"$link\" -> \"${newLink}\"" }
                }
                ResolvedLink(newLink)
            } else {
                ResolvedLink(link)
            }
        }.onLeft {
            log.e(it) { "Failed to resolve link $link" }
        }.fold({ ResolvedLink(link) }, { it })
            .right()

    companion object {
        private val log = Logger.withTag("LinkResolver")

        /**
         * Resolved links often end up in the format facebook.com/login/?next=...
         * e.g. https://www.facebook.com/login/?next=https%3A%2F%2Fwww.facebook.com%2F239996942266_1098642475632096
         * Return the decoded next part in these cases
         */
        fun removeLogin(link: Url) =
            link.takeUnless {
                it.host.endsWith("facebook.com") && it.segments == listOf("login")
            }
                ?: link.parameters["next"]?.decodeURLPart()?.let(::Url)
                ?: run {
                    log.v { "Failed to remove login from $link" }
                    link
                }
    }
}
