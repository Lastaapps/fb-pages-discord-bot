package cz.lastaapps.api.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LazyProvider<S : Any, T : Any>(
    val id: S,
    private val construct: suspend () -> T,
) {
    private val mutex = Mutex()
    private var value: T? = null

    suspend operator fun invoke(): T = mutex.withLock {
        value ?: construct().also { value = it }
    }
}
