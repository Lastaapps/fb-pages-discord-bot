package cz.lastaapps.api

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message

fun configureSentry(sentryDsn: String, debug: Boolean = false) {
    Sentry.init { options ->
        options.dsn = sentryDsn
        options.isDebug = debug
        options.environment = if (debug) "development" else "production"
    }
}

class SentryLogWriter() : LogWriter() {
    private fun Severity.toSentryLevel(): SentryLevel = when (this) {
        Severity.Verbose, Severity.Debug -> SentryLevel.DEBUG
        Severity.Info -> SentryLevel.INFO
        Severity.Warn -> SentryLevel.WARNING
        Severity.Error -> SentryLevel.ERROR
        Severity.Assert -> SentryLevel.FATAL
    }

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        when (severity) {
            Severity.Assert,
            Severity.Error,
            Severity.Warn,
                -> {
                val sentryEvent = SentryEvent(throwable).apply {
                    this.level = severity.toSentryLevel()
                    this.message = Message().also { it.message = message }
                    this.setTag("kermit-tag", tag)
                }
                Sentry.captureEvent(sentryEvent)
            }

            Severity.Info -> {
                val breadcrumb = Breadcrumb.info(message).also {
                    it.level = severity.toSentryLevel()
                }
                Sentry.addBreadcrumb(breadcrumb)
            }

            Severity.Debug -> {
                val breadcrumb = Breadcrumb.debug(message).also {
                    it.level = severity.toSentryLevel()
                }
                Sentry.addBreadcrumb(breadcrumb)
            }

            Severity.Verbose -> {}
        }
    }
}
