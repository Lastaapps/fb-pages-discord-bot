package cz.lastaapps.api.di

import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.DiscordAPI
import cz.lastaapps.api.data.FBAuthAPI
import cz.lastaapps.api.data.FBDataAPI
import cz.lastaapps.api.data.ManagementRepo
import cz.lastaapps.api.data.PostsRepo
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.usecase.AddPageUC
import cz.lastaapps.api.domain.usecase.GetAuthorizedPagesUC
import cz.lastaapps.api.domain.usecase.GetOAuthLink
import cz.lastaapps.api.domain.usecase.GetPagesForChannelUC
import cz.lastaapps.api.domain.usecase.ParsePageIDUC
import cz.lastaapps.api.domain.usecase.RemovePageUC
import cz.lastaapps.api.domain.usecase.RunJobsUC
import cz.lastaapps.api.domain.usecase.VerifyUserPagesUC
import cz.lastaapps.api.presentation.DCCommandManager
import cz.lastaapps.api.presentation.RestAPI
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val log = Logger.withTag("DI")
val diModule = module {
    singleOf(::ManagementRepo)
    single { createHttpClient() }
    single { Clock.System } bind Clock::class

    singleOf(::DCCommandManager)
    singleOf(::RestAPI)
    singleOf(::AppTokenProvider)
    singleOf(::PostsRepo)

    factoryOf(::FBAuthAPI)
    factoryOf(::FBDataAPI)
    factoryOf(::DiscordAPI)

    factoryOf(::AddPageUC)
    factoryOf(::GetOAuthLink)
    factoryOf(::GetAuthorizedPagesUC)
    factoryOf(::GetPagesForChannelUC)
    factoryOf(::ParsePageIDUC)
    factoryOf(::RemovePageUC)
    factoryOf(::VerifyUserPagesUC)
    factoryOf(::RunJobsUC)
}

private fun createHttpClient() =
    HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            logger =
                object : io.ktor.client.plugins.logging.Logger {
                    private val tokenRegexes = listOf(
                        """access_token=[^?&#]*""".toRegex(),
                        """client_id=[^?&#]*""".toRegex(),
                        """client_secret=[^?&#]*""".toRegex(),
                    )

                    override fun log(message: String) {
                        log.v {
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
    }
