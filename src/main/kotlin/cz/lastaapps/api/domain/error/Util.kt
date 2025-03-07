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
import kotlin.time.Duration.Companion.seconds

suspend fun <T> catchingNetwork(
    block: suspend Raise<DomainError>.() -> T,
): Outcome<T> =
    Either.catch { either { block() } }.mapLeft {
        Logger.withTag("catchingNetwork").e(it) { "Failed network call" }

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
    block: suspend Raise<DomainError>.() -> T,
): Outcome<T> = Schedule
    .recurs<DomainError>(3)
    .and(Schedule.exponential(5.seconds))
    // retry if a timeout exception occurred
    .doWhile { input, _ -> input is NetworkError.Timeout }
    .retryEither { catchingNetwork(block) }

suspend inline fun RoutingCall.respondError(error: DomainError) =
    respond(error.httpCode(), error.text())
