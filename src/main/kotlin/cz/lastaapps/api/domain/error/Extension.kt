package cz.lastaapps.api.domain.error

import co.touchlab.kermit.Logger

fun Logger.e(error: DomainError, tag: String = this.tag, message: () -> String) = e(tag = tag) {
    buildString {
        message().trim().let(::append)
        append('\n')

        error.extraMessage?.let {
            append(it.trim())
            append('\n')
        }

        error.text().trim().let(::append)
        append('\n')

        error.throwable?.stackTraceToString()?.trim()?.let(::append)?.let { append('\n') }
    }.trim()
}
