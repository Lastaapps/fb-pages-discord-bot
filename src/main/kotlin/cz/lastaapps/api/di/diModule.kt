package cz.lastaapps.api.di

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.api.DiscordAPI
import cz.lastaapps.api.data.api.FBAuthAPI
import cz.lastaapps.api.data.api.FBDataAPI
import cz.lastaapps.api.data.provider.EventProvider
import cz.lastaapps.api.data.provider.LinkResolver
import cz.lastaapps.api.data.provider.LocationConverter
import cz.lastaapps.api.data.provider.PostProvider
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.data.repo.ProcessingRepo
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.usecase.AddPageUC
import cz.lastaapps.api.domain.usecase.ChangeChannelEnabledUC
import cz.lastaapps.api.domain.usecase.GetAuthorizedPagesUC
import cz.lastaapps.api.domain.usecase.GetOAuthLink
import cz.lastaapps.api.domain.usecase.GetPagesForChannelUC
import cz.lastaapps.api.domain.usecase.ParsePageIDUC
import cz.lastaapps.api.domain.usecase.RemovePageUC
import cz.lastaapps.api.domain.usecase.RunJobsUC
import cz.lastaapps.api.domain.usecase.SearchPagesUC
import cz.lastaapps.api.domain.usecase.VerifyUserPagesUC
import cz.lastaapps.api.presentation.AppConfig
import cz.lastaapps.api.presentation.DCCommandManager
import cz.lastaapps.api.presentation.RestAPI
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val diModule = module {
    singleOf(::ManagementRepo)
    single { createHttpClient(get()) }
    single { Clock.System } bind Clock::class

    singleOf(::DCCommandManager)
    singleOf(::RestAPI)
    singleOf(::AppTokenProvider)
    singleOf(::ProcessingRepo)

    factoryOf(::FBAuthAPI)
    factoryOf(::FBDataAPI)
    factoryOf(::DiscordAPI)
    factoryOf(::LinkResolver)
    factoryOf(::PostProvider)
    factoryOf(::EventProvider)
    factoryOf(::LocationConverter)

    factoryOf(::AddPageUC)
    factoryOf(::GetOAuthLink)
    factoryOf(::GetAuthorizedPagesUC)
    factoryOf(::GetPagesForChannelUC)
    factoryOf(::ParsePageIDUC)
    factoryOf(::RemovePageUC)
    factoryOf(::ChangeChannelEnabledUC)
    factoryOf(::SearchPagesUC)
    factoryOf(::VerifyUserPagesUC)
    factoryOf(::RunJobsUC)
}

private fun createHttpClient(
    config: AppConfig,
) =
    when (config.networking.clientHttpEngine) {
        AppConfig.Networking.HttpEngine.CIO -> HttpClient(CIO) {}
        AppConfig.Networking.HttpEngine.OKHTTP -> HttpClient(OkHttp) {
            engine {
                config {
                    // Already default
                    // protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
                }
                pipelining = true
            }
        }
    }.config {
        install(Logging) {
            level = config.logging.logLevelHttp
            logger =
                object : io.ktor.client.plugins.logging.Logger {
                    private val log = Logger.withTag("HttpClient")
                    private val tokenRegexes = listOf(
                        """access_token=[^?&#]*""".toRegex(),
                        """client_id=[^?&#]*""".toRegex(),
                        """client_secret=[^?&#]*""".toRegex(),
                    )

                    override fun log(message: String) {
                        log.d {
                            message.let {
                                tokenRegexes.fold(it) { acc, regex ->
                                    regex.replace(acc, "redacted=XXX")
                                }
                            }.replace("\n", "\\n\t")
                        }
                    }
                }
        }
        install(DefaultRequest) {
            url(
                scheme = "https",
                host = "graph.facebook.com",
            )
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        // Attempts to speed up network requests to reduce rate limiting
        if (config.networking.compressResponses) {
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
                mode = ContentEncodingConfig.Mode.DecompressResponse
            }
        }
    }
