package cz.lastaapps.api.domain.error

sealed interface DomainError {
    data object PageNotAuthorized : DomainError
    data object PageAlreadyLinkedToChannel : DomainError
    data object GivenPageNotFound : DomainError
}

fun DomainError.text() = when (this) {
    else -> this::class.simpleName
}
