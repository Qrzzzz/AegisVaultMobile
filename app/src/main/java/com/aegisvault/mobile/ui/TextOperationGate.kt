package com.aegisvault.mobile.ui

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide budget: a cancelled synchronous KDF still owns its slot until it exits. */
internal object TextOperationGate {
    private val mutex = Mutex()

    suspend fun <T> run(operation: suspend () -> T): T = mutex.withLock {
        currentCoroutineContext().ensureActive()
        operation()
    }
}
