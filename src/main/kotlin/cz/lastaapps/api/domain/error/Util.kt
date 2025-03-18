package cz.lastaapps.api.domain.error

import arrow.core.Either
import arrow.core.flatten
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.resilience.Schedule
import arrow.resilience.retryEither
import co.touchlab.kermit.Logger
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = Logger.withTag("catchingNetwork")

suspend fun <T> catchingNetwork(
    block: suspend Raise<DomainError>.() -> T,
): Outcome<T> =
    Either.catch { either { block() } }.mapLeft {
        log.e { "Failed network call: ${it.message}" }

        when (it::class.simpleName) {
            "TimeoutException",
            "HttpRequestTimeoutException",
            "SocketTimeoutException",
            "OutOfSpaceException", // somehow thrown inside the KTor HttpRequestTimeoutException constructor
                -> NetworkError.Timeout

            "ConnectException",
                -> NetworkError.ConnectionClosed

            "UnknownHostException",
            "NoRouteToHostException",
            "IOException",
            "SSLException",
            "SocketException",
                -> NetworkError.NoInternet

            "JsonConvertException",
            "JsonDecodingException",
                -> NetworkError.SerializationError(it)

            else -> DomainError.Unknown(it, "Network request error")
        }
    }.flatten()

suspend fun <T> catchingDiscord(
    block: suspend Raise<DomainError>.() -> T,
): Outcome<T> = catchingNetwork(block)

suspend fun <T> catchingFacebookAPI(
    reoccurs: Long = 6,
    initialDelay: Duration = 10.seconds, // 10, 20, 40, 1:20, 2:40, 5:20 | 10:40 21:20
    callID: Int = Random.nextInt(1000),
    block: suspend Raise<DomainError>.() -> T,
): Outcome<T> = Schedule
    // up to k retries -> k+1 in total
    .recurs<DomainError>(reoccurs)
    // has to be long so rate limit is not reached
    .and(Schedule.exponential(initialDelay))
    // retry if a timeout exception occurred
    .doWhile { input, (retryIndex, _) ->
        when (input) {
            is NetworkError.Timeout -> {
                log.w { "Retrying network call ($callID) because of a timeout (retry: $retryIndex)" }
                true
            }

            is NetworkError.FBAPIError if input.isRateLimit -> {
                log.w { "Retrying network call ($callID) because of rate limit (retry: $retryIndex)" }
                true
            }

            else -> {
                log.v { "Retry skipped for call ($callID) as error is ${input::class.simpleName}" }
                false
            }
        }
    }
    .retryEither {
        log.v { "Performing recoverable network call ($callID)" }
        catchingNetwork(block)
    }

suspend inline fun RoutingCall.respondError(error: DomainError) =
    respond(error.httpCode(), error.text())
