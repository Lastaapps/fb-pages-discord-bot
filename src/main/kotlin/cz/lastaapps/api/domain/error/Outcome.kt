package cz.lastaapps.api.domain.error

import arrow.core.Either

typealias Outcome<T> = Either<DomainError, T>
