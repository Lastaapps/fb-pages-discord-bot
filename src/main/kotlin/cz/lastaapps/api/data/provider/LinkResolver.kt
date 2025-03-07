package cz.lastaapps.api.data.provider

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

class LinkResolver(
    client: HttpClient = HttpClient(),
) {
    private val client = client.config {
        followRedirects = true
        BrowserUserAgent()
        install(Logging) {
            level = LogLevel.NONE
        }
    }

    suspend fun resolve(link: String): Outcome<ResolvedLink> = catchingNetwork {
        // yes, this is a potential security risk - remote url execution or something
        val response = client.head(link.trim())
        ResolvedLink(response.call.request.url)
    }
        .onRight {
            if (it.link.toString() != link) {
                log.i { "Resolved \"$link\" -> \"${it.link}\"" }
            }
        }
        .onLeft {
            log.e(it) { "Failed to resolve link $link" }
        }

    companion object {
        private val log = Logger.withTag("LinkResolver")
    }
}
