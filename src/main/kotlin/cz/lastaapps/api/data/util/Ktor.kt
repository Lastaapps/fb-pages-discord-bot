package cz.lastaapps.api.data.util

import arrow.core.Either
import arrow.core.raise.Raise
import cz.lastaapps.api.data.model.FBError
import cz.lastaapps.api.domain.error.DomainError
import cz.lastaapps.api.domain.error.NetworkError
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

context(Raise<DomainError>)
suspend inline fun <reified T> HttpResponse.bindBody(): T =
    Either.catch {
        if (status.isSuccess()) {
            body<T>()
        } else {
            raise(
                NetworkError.FBAPIError(
                    status,
                    body<FBError.Wrapper>().error,
                ),
            )
        }
    }.mapLeft {
        NetworkError.SerializationError(it, responseBody = bodyAsText())
    }.bind()
