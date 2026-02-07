package cz.lastaapps.api.data.api

import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import cz.lastaapps.api.API_VERSION
import cz.lastaapps.api.data.model.FBManagedPages
import cz.lastaapps.api.data.model.FBMeResponse
import cz.lastaapps.api.data.model.FBOAuthExchangeResponse
import cz.lastaapps.api.data.model.FBPageInfo
import cz.lastaapps.api.data.util.bindBody
import cz.lastaapps.api.domain.error.LogicError
import cz.lastaapps.api.domain.error.Outcome
import cz.lastaapps.api.domain.error.catchingFacebookAPI
import cz.lastaapps.api.domain.model.AuthorizedPageFromUser
import cz.lastaapps.api.domain.model.id.FBPageID
import cz.lastaapps.api.domain.model.token.AppAccessToken
import cz.lastaapps.api.domain.model.token.PageAccessToken
import cz.lastaapps.api.domain.model.token.UserAccessToken
import cz.lastaapps.api.presentation.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FBAuthAPI(
    httpClient: HttpClient,
    private val config: AppConfig,
    clock: Clock,
) {
    private val stateManager: OAuthStateManager = OAuthStateManager(clock = clock)
    private val log = Logger.withTag("AuthAPI")

    private val client =
        httpClient.config {
            expectSuccess = true
            followRedirects = true
        }

    /**
     * https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow
     * https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived
     * https://developers.facebook.com/docs/facebook-login/facebook-login-for-business
     */
    suspend fun createOAuthURL() =
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
    suspend fun exchangeOAuth(parameters: Parameters): Outcome<UserAccessToken> =
        catchingFacebookAPI {
            val code = parameters["code"]!!
            val state = parameters["state"]!!
            log.d { "Exchange OAuth ${state.substring(1..5)}" }

            stateManager.validateState(state).bind()

            val response =
                client.get(
                    "/$API_VERSION/oauth/access_token?" +
                        "client_id=${config.facebook.appID}" +
                        "&redirect_uri=${config.facebook.loginRedirectURL!!.encodeURLParameter()}" +
                        "&client_secret=${config.facebook.appSecret.encodeURLParameter()}" +
                        "&code=${code.encodeURLParameter()}",
                )
            log.d { "Exchange result: ${response.status}" }
            val data = response.bindBody<FBOAuthExchangeResponse>()
            data.userAccessToken
        }

    suspend fun grantAccessToUserPages(userAccessToken: UserAccessToken): Outcome<List<AuthorizedPageFromUser>> =
        catchingFacebookAPI {
            log.d { "Granting user access" }
            val user =
                client
                    .get("/$API_VERSION/me") {
                        parameter("fields", "id,name")
                        parameter("access_token", userAccessToken.token)
                    }.let { response ->
                        log.d { "Status code: ${response.status}" }
                        println(response.bodyAsText())
                        response.bindBody<FBMeResponse>()
                    }

            log.d { "User - id: ${user.fbId.id}, name: ${user.name}" }
            client
                .get(
                    "/$API_VERSION/${user.fbId.id}/accounts",
                ) {
                    parameter("access_token", userAccessToken.token)
                }.let { response ->
                    log.d { "Status code: ${response.status}" }
                    response.bindBody<FBManagedPages>().data
                }.parMap {
                    val info = getPageMetadata(it.fbId, it.pageAccessToken).bind()
                    AuthorizedPageFromUser(
                        user.fbId,
                        user.name,
                        userAccessToken,
                        info.fbId,
                        info.name,
                        it.pageAccessToken,
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
    ): Outcome<FBPageInfo> =
        catchingFacebookAPI {
            log.d { "Loading page info $pageIDOrUrlPart" }
            client
                .get("/$API_VERSION/$pageIDOrUrlPart") {
                    parameter("access_token", pageAccessToken.token)
                }.let { response ->
                    log.d { "Status code: ${response.status}" }
                    response.bindBody<FBPageInfo>()
                }
        }

    suspend fun getAppAccessToken(): Outcome<AppAccessToken> =
        catchingFacebookAPI {
            log.d { "Obtaining app access token" }

            val response =
                client.get(
                    "/$API_VERSION/oauth/access_token?" +
                        "client_id=${config.facebook.appID}" +
                        "&client_secret=${config.facebook.appSecret.encodeURLParameter()}" +
                        "&grant_type=client_credentials",
                )
            log.d { "Exchange result: ${response.status}" }
            val data = response.bindBody<FBOAuthExchangeResponse>()
            data.appAccessToken
        }

    private class OAuthStateManager(
        private val clock: Clock,
        private val stateTimeout: Duration = 5.minutes,
        private val random: SecureRandom = SecureRandom(),
    ) {
        private val statesMap = hashMapOf<String, Instant>()
        private val mutex = Mutex()

        suspend fun nextState(): String =
            ByteArray(32)
                .also { random.nextBytes(it) }
                .let(Base64::encode)
                .also {
                    mutex.withLock {
                        statesMap[it] = clock.now()
                    }
                }

        suspend fun validateState(state: String): Outcome<Unit> =
            mutex.withLock {
                val threshold = clock.now() - stateTimeout
                statesMap.keys.removeAll { statesMap[it]!! < threshold }
                if (statesMap[state] == null) {
                    LogicError.InvalidOAuthState.left()
                } else {
                    statesMap.remove(state)
                    Unit.right()
                }
            }
    }
}
