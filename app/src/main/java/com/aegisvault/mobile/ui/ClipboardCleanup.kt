package com.aegisvault.mobile.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ClipboardOwnership(val token: String, val digest: String, val timestamp: Long?)
internal enum class ClipboardClearResult { CLEARED, REPLACED, UNAVAILABLE }
internal interface SensitiveClipboardPort {
    fun copy(text: String, token: String): ClipboardOwnership
    fun clearIfOwned(owner: ClipboardOwnership): ClipboardClearResult
}

/** Main-thread owner, independent of Activity/Composition. Stores no plaintext or disk state. */
internal class ClipboardCleanup(
    private val port: SensitiveClipboardPort,
    private val scope: CoroutineScope,
    private val elapsedMillis: () -> Long,
    private val newToken: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private data class Pending(val owner: ClipboardOwnership, val copiedAt: Long, val manual: Boolean = false)
    private var pending: Pending? = null
    private var seconds: Long? = null
    private var timer: Job? = null

    fun copy(text: String): String {
        val owner = port.copy(text, newToken())
        pending = Pending(owner, elapsedMillis())
        schedule()
        return owner.token
    }

    fun setTimeout(value: Long?) {
        require(value == null || value in 1..300)
        if (seconds == value) return
        seconds = value
        schedule()
    }

    fun clear(token: String) {
        val current = pending ?: return
        if (current.owner.token != token) return
        pending = current.copy(manual = true)
        schedule()
    }

    // A due background attempt may be denied; retry once when the window regains focus.
    fun onForeground() { schedule() }

    private fun schedule() {
        timer?.cancel()
        timer = null
        val current = pending ?: return
        val deadline = if (current.manual) current.copiedAt else
            seconds?.let { current.copiedAt + it * 1000 } ?: return
        val remaining = deadline - elapsedMillis()
        if (remaining <= 0) attempt(current)
        else timer = scope.launch {
            delay(remaining)
            if (pending === current) attempt(current)
        }
    }

    private fun attempt(current: Pending) {
        if (pending !== current) return
        when (port.clearIfOwned(current.owner)) {
            ClipboardClearResult.CLEARED, ClipboardClearResult.REPLACED -> pending = null
            ClipboardClearResult.UNAVAILABLE -> Unit // Keep the deadline, without polling.
        }
    }
}
