package cz.lastaapps.api

import arrow.fx.coroutines.parMap
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.util.encodeBase64
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AuthAPI(
    private val client: HttpClient,
    private val config: AppConfig,
    clock: Clock = Clock.System,
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
            "&redirect_uri=${config.facebook.redirectURL.encodeURLParameter()}" +
            // TODO check with other config
            "&config_id=${config.facebook.configID}" +
            "&state=${
                stateManager.nextState().also { log.d { "Created OAuth redirect: ${it.substring(0..5)}" } }
                    .encodeURLParameter()
            }"

    /**
     * https://developers.facebook.com/tools/explorer
     */
    suspend fun exchangeOAuth(parameters: Parameters): String {
        val code = parameters["code"]!!
        val state = parameters["state"]!!
        log.d { "Exchange OAuth ${state.substring(1..5)}" }

        stateManager.validateState(state)

        val response =
            client.get(
                "/${API_VERSION}/oauth/access_token?" +
                    "client_id=${config.facebook.appID}" +
                    "&redirect_uri=${config.facebook.redirectURL.encodeURLParameter()}" +
                    "&client_secret=${config.facebook.appSecret.encodeURLParameter()}" +
                    "&code=${code.encodeURLParameter()}",
            )
        log.d { "Exchange result: ${response.status}" }
        val data = response.body<OAuthExchangeResponse>()
        return data.accessToken
    }

    suspend fun grantAccess(userAccessToken: String): List<AuthorizedPageFromUser> {
        log.d { "Granting user access" }
        val (userId, userName) =
            client
                .get("/${API_VERSION}/me") {
                    parameter("fields", "id,name")
                    parameter("access_token", userAccessToken)
                }.let { response ->
                    log.d { "Status code: ${response.status}" }
                    response.body<MeResponse>()
                }
        log.d { "User - id: $userId, name: $userName" }
        return client
            .get(
                "/${API_VERSION}/$userId/accounts",
            ) {
                parameter("access_token", userAccessToken)
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<ManagedPages>().data
            }.parMap {
                val info = loadPageInfo(client, it.id, it.pageAccessToken)
                AuthorizedPageFromUser(
                    userId,
                    userName,
                    userAccessToken,
                    info.id,
                    info.name,
                    it.pageAccessToken,
                )
            }
    }

    private suspend fun loadPageInfo(
        client: HttpClient,
        pageID: String,
        pageAccessToken: String,
    ): PageInfo {
        log.d { "Loading page info $pageID" }
        return client
            .get("/${API_VERSION}/$pageID") {
                parameter("access_token", pageAccessToken)
            }.let { response ->
                log.d { "Status code: ${response.status}" }
                response.body<PageInfo>()
            }
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
