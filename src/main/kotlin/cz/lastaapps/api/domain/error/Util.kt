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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = Logger.withTag("catchingNetwork")

suspend fun <T> catchingNetwork(block: suspend Raise<DomainError>.() -> T): Outcome<T> =
    Either
        .catch { either { block() } }
        .mapLeft {
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

suspend fun <T> catchingDiscord(block: suspend Raise<DomainError>.() -> T): Outcome<T> = catchingNetwork(block)

suspend fun <T> catchingFacebookAPI(
    callID: Int = Random.nextInt(1000),
    block: suspend Raise<DomainError>.() -> T,
): Outcome<T> =
    timeoutSchedule(callID)
        .or(
            rateLimitSchedule(callID),
            transform =
                transformNulls { o1, o2 ->
                    maxOf(o1.first, o2.first) to maxOf(o1.second, o2.second)
                },
            combineDuration =
                transformNulls { o1, o2 ->
                    maxOf(o1, o2)
                },
        ).retryEither {
            log.v { "Performing recoverable network call ($callID)" }
            catchingNetwork(block)
        }

private fun <T : Any> transformNulls(transform: (T, T) -> T): (T?, T?) -> T =
    { a: T?, b: T? ->
        when {
            a == null -> b!!
            b == null -> a
            else -> transform(a, b)
        }
    }

private fun timeoutSchedule(
    callID: Int,
    reoccurs: Long = 6,
    initialDelay: Duration = 10.seconds, // 10, 20, 40, 1:20, 2:40, 5:20 | 10:40 21:20
) = Schedule
    .recurs<DomainError>(reoccurs)
    .and(Schedule.exponential(initialDelay))
    .doWhile { input, (retryIndex, _) ->
        when (input) {
            is NetworkError.Timeout -> {
                log.w { "Retrying network call ($callID) because of a timeout (retry: $retryIndex)" }
                true
            }

            else -> {
                false
            }
        }
    }

private fun rateLimitSchedule(
    callID: Int,
    reoccurs: Long = 6,
    initialDelay: Duration = 1.minutes,
) = Schedule
    .recurs<DomainError>(reoccurs)
    .and(Schedule.exponential(initialDelay))
    .jittered()
    .doWhile { input, (retryIndex, _) ->
        when (input) {
            is NetworkError.FBAPIError if input.isRateLimit -> {
                log.w { "Retrying network call ($callID) because of rate limit (retry: $retryIndex)" }
                true
            }

            else -> {
                false
            }
        }
    }

suspend inline fun RoutingCall.respondError(error: DomainError) = respond(error.httpCode(), error.text())
