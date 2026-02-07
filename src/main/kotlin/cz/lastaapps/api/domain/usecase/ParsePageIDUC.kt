package cz.lastaapps.api.domain.usecase

import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import cz.lastaapps.api.data.api.FBAuthAPI
import cz.lastaapps.api.data.repo.ManagementRepo
import cz.lastaapps.api.domain.AppTokenProvider
import cz.lastaapps.api.domain.error.DomainError
import cz.lastaapps.api.domain.error.LogicError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.token.toPageAccessToken
import cz.lastaapps.api.presentation.AppConfig
import io.ktor.http.Url

class ParsePageIDUC(
    private val config: AppConfig,
    private val repo: ManagementRepo,
    private val authApi: FBAuthAPI,
    private val appTokenProvider: AppTokenProvider,
) {
    private val log = Logger.withTag("ParsePageID")

    @Suppress("RemoveExplicitTypeArguments")
    suspend operator fun invoke(
        pageReference: String,
        allowUrl: Boolean = true,
    ): Outcome<FBPageID> =
        either<DomainError, FBPageID> {
            // Page ID
            pageReference.toULongOrNull()?.let { return@either FBPageID(it) }

            // Page Name
            pageReference.trim().takeIf { it.isNotBlank() }?.let { pageName ->
                val res =
                    repo
                        .getPageIDByName(pageName)
                        .bind()
                        .flatMap { repo.getPageByID(it).bind() }
                when (res) {
                    None -> {}

                    is Some -> {
                        return@either res.value.fbId
                    }
                }
            }

            if (config.facebook.enabledPublicContent && !pageReference.contains('/')) {
                authApi
                    .getPageMetadata(pageReference, appTokenProvider.provide().bind().toPageAccessToken())
                    .onRight {
                        return@either it.fbId
                    }
            }

            if (allowUrl) {
                Either.catch {
                    val url = Url(pageReference)

                    // This ID does not work in the Graph API for some reason
//                    url.parameters["id"]?.let { pageID ->
//                        return invoke(pageID, allowUrl = false)
//                    }

                    val pageIDPath =
                        url
                            .segments
                            .first()
                    return invoke(pageIDPath, allowUrl = false)
                }
            }

            raise(LogicError.GivenPageNotFound)
        }.also {
            it
                .onRight {
                    log.d { "Parsed '$pageReference' -> '${it.id}'" }
                }.onLeft {
                    log.d { "Failed to parse '$pageReference'" }
                }
        }
}
