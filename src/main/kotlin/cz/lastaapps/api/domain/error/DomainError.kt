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
    LogicError.CannotAccessServerName -> "Cannot access server name"

    NetworkError.ConnectionClosed -> "Connection closed"
    NetworkError.NoInternet -> "No internet connection"
    NetworkError.Timeout -> "Request timed out"
    is NetworkError.SerializationError -> "Failed to parse response " + throwable.localizedMessage
    is NetworkError.FBAPIError -> "FB API error: $error"
}

fun DomainError.httpCode(): HttpStatusCode = with(HttpStatusCode.Companion) {
    when (this@httpCode) {
        is DomainError.Unknown -> InternalServerError
        LogicError.GivenPageNotFound -> NotFound
        LogicError.InvalidOAuthState -> BadRequest
        LogicError.PageAlreadyLinkedToChannel -> BadRequest
        LogicError.PageNotAuthorized -> BadRequest
        LogicError.CannotAccessServerName -> Forbidden
        is NetworkError -> InternalServerError
    }
}
