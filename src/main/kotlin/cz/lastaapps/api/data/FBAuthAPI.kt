package cz.lastaapps.api.data

import arrow.core.raise.either
import arrow.core.right
import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import cz.lastaapps.api.API_VERSION
import cz.lastaapps.api.data.model.ManagedPages
import cz.lastaapps.api.data.model.MeResponse
import cz.lastaapps.api.data.model.OAuthExchangeResponse
import cz.lastaapps.api.data.model.PageInfo
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.model.AuthorizedPageFromUser
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.id.FBUserID
import cz.lastaapps.api.domain.model.token.AppAccessToken
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.UserAccessToken
import cz.lastaapps.api.presentation.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.util.encodeBase64
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FBAuthAPI(
    private val client: HttpClient,
    private val config: AppConfig,
    clock: Clock,
) {
    private val stateManager: OAuthStateManager = OAuthStateManager(clock = clock)
    private val log = Logger.withTag("AuthAPI")

    /**
     * https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow
     * https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived
     * https://developers.facebook.com/docs/facebook-login/facebook-login-for-business
     */
    fun createOAuthURL() =
        "https://www.facebook.com/$API_VERSION/dialog/oauth?" +
            "client_id=${config.facebook.appID}" +
            "&redirect_uri=${config.facebook.loginRedirectURL!!.encodeURLParameter()}" +
            "&config_id=${config.facebook.loginConfigID}" +
            "&state=${
                stateManager.nextState().also { log.d { "Created OAuth redirect: ${it.substring(0..5)}" } }
                    .encodeURLParameter()
            }"

    /**
     * Calls Facebook API to obtain user access token
     *
     * https://developers.facebook.com/tools/explorer
     */
    suspend fun exchangeOAuth(parameters: Parameters): UserAccessToken {
        val code = parameters["code"]!!
        val state = parameters["state"]!!
        log.d { "Exchange OAuth ${state.substring(1..5)}" }

        stateManager.validateState(state)

        val response =
            client.get(
                "/$API_VERSION/oauth/access_token?" +
                    "client_id=${config.facebook.appID}" +
                    "&redirect_uri=${config.facebook.loginRedirectURL!!.encodeURLParameter()}" +
                    "&client_secret=${config.facebook.appSecret.encodeURLParameter()}" +
                    "&code=${code.encodeURLParameter()}",
            )
        log.d { "Exchange result: ${response.status}" }
        val data = response.body<OAuthExchangeResponse>()
        return UserAccessToken(data.accessToken)
    }

    suspend fun grantAccessToUserPages(userAccessToken: UserAccessToken): Outcome<List<AuthorizedPageFromUser>> =
        either {
        log.d { "Granting user access" }
        val (userId, userName) =
            client
                .get("/$API_VERSION/me") {
                    parameter("fields", "id,name")
                    parameter("access_token", userAccessToken.token)
                }.let { response ->
                    log.d { "Status code: ${response.status}" }
                    println(response.bodyAsText())
                    response.body<MeResponse>()
                }
        log.d { "User - id: $userId, name: $userName" }
            client
            .get(
                "/$API_VERSION/$userId/accounts",
            ) {
                parameter("access_token", userAccessToken.token)
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<ManagedPages>().data
            }.parMap {
                    val info = getPageMetadata(FBPageID(it.id.toULong()), PageAccessToken(it.pageAccessToken)).bind()
                AuthorizedPageFromUser(
                    userId.toULong().let(::FBUserID),
                    userName,
                    userAccessToken,
                    info.fbId.toULong().let(::FBPageID),
                    info.name,
                    PageAccessToken(it.pageAccessToken),
                )
            }
    }

    suspend fun getPageMetadata(
        pageID: FBPageID,
        pageAccessToken: PageAccessToken,
    ) = getPageMetadata(pageID.id.toString(), pageAccessToken)

    suspend fun getPageMetadata(
        pageIDOrUrlPart: String,
        pageAccessToken: PageAccessToken,
    ): Outcome<PageInfo> {
        log.d { "Loading page info $pageIDOrUrlPart" }
        return client
            .get("/$API_VERSION/${pageIDOrUrlPart}") {
                parameter("access_token", pageAccessToken.token)
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<PageInfo>()
            }.right()
    }

    suspend fun getAppAccessToken(): AppAccessToken {
        log.d { "Obtaining app access token" }

        val response =
            client.get(
                "/$API_VERSION/oauth/access_token?" +
                    "client_id=${config.facebook.appID}" +
                    "&client_secret=${config.facebook.appSecret.encodeURLParameter()}" +
                    "&grant_type=client_credentials",
            )
        log.d { "Exchange result: ${response.status}" }
        val data = response.body<OAuthExchangeResponse>()
        return AppAccessToken(data.accessToken)
    }

    private class OAuthStateManager(
        private val clock: Clock,
        private val stateTimeout: Duration = 5.minutes,
    ) {
        private val statesMap = hashMapOf<String, Instant>()
        private val random = SecureRandom()

        fun nextState(): String =
            ByteArray(32)
                .also { random.nextBytes(it) }
                .encodeBase64()
                .also {
                    System.out.flush()
                    statesMap[it] = clock.now()
                }

        fun validateState(state: String) {
            val threshold = clock.now() - stateTimeout
            statesMap.keys.removeAll { statesMap[it]!! < threshold }
            if (statesMap[state] == null) {
                throw IllegalStateException("Invalid state")
            }
        }
    }
}
