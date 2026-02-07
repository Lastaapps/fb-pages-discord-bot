package cz.lastaapps.api.domain.error

sealed interface LogicError : DomainError {
    data object PageNotAuthorized : LogicError

    data object PageAlreadyLinkedToChannel : LogicError

    data object GivenPageNotFound : LogicError

    data object InvalidOAuthState : LogicError

    data object CannotAccessServerName : LogicError
}
