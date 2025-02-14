package cz.lastaapps.api.domain.error

import io.ktor.http.HttpStatusCode

sealed interface DomainError {

    val throwable: Throwable?
        get() = null

    val extraMessage: String?
        get() = null

    data class Unknown(
        override val throwable: Throwable,
        override val extraMessage: String? = null,
    ) : DomainError
}

fun DomainError.text(): String = when (this) {
    is DomainError.Unknown -> throwable.localizedMessage + " " + extraMessage.orEmpty()
    LogicError.GivenPageNotFound -> "Page not found, invalid id specified"
    LogicError.InvalidOAuthState -> "Invalid OAuth state"
    LogicError.PageAlreadyLinkedToChannel -> "Page already linked to channel"
    LogicError.PageNotAuthorized -> "Page not authorized"
    NetworkError.ConnectionClosed -> "Connection closed"
    NetworkError.NoInternet -> "No internet connection"
    is NetworkError.SerializationError -> "Failed to parse response"
    NetworkError.Timeout -> "Request timed out"
}

fun DomainError.httpCode(): HttpStatusCode = with(HttpStatusCode.Companion) {
    when (this@httpCode) {
        is DomainError.Unknown -> InternalServerError
        LogicError.GivenPageNotFound -> NotFound
        LogicError.InvalidOAuthState -> BadRequest
        LogicError.PageAlreadyLinkedToChannel -> BadRequest
        LogicError.PageNotAuthorized -> BadRequest
        is NetworkError -> InternalServerError
    }
}
