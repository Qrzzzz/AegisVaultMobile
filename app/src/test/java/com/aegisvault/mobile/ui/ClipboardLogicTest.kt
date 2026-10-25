package com.aegisvault.mobile.ui

import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardLogicTest {
    private class Port : SensitiveClipboardPort {
        var value: String? = null
        var owner: ClipboardOwnership? = null
        var readable = true
        var attempts = 0
        private var stamp = 0L
        override fun copy(text: String, token: String): ClipboardOwnership {
            value = text
            return ClipboardOwnership(token, text, ++stamp).also { owner = it }
        }
        override fun clearIfOwned(owner: ClipboardOwnership): ClipboardClearResult {
            attempts++
            if (!readable) return ClipboardClearResult.UNAVAILABLE
            if (this.owner != owner) return ClipboardClearResult.REPLACED
            value = null; this.owner = null
            return ClipboardClearResult.CLEARED
        }
    }
    private fun TestScope.cleanup(port: Port) = ClipboardCleanup(port, backgroundScope, { testScheduler.currentTime })

    @Test fun expiryUsesTheOriginalCopyDeadlineDespiteForegroundCallbacks() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        cleanup.setTimeout(30); cleanup.copy("synthetic-secret"); runCurrent()
        advanceTimeBy(20_000); cleanup.onForeground(); runCurrent()
        advanceTimeBy(10_001); runCurrent()
        assertNull(port.value); assertEquals(1, port.attempts)
    }

    @Test fun identicalCopiesReplaceTheOldDeadlineAndManualActionToken() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        cleanup.setTimeout(30)
        val old = cleanup.copy("same"); runCurrent(); advanceTimeBy(20_000)
        val newer = cleanup.copy("same"); runCurrent(); cleanup.clear(old)
        advanceTimeBy(10_001); runCurrent()
        assertEquals("same", port.value); assertNotEquals(old, newer)
        advanceTimeBy(20_000); runCurrent(); assertNull(port.value)
    }

    @Test fun oldManualClearCannotDeleteANewerDifferentCopy() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        val old = cleanup.copy("old"); val newer = cleanup.copy("new")
        cleanup.clear(old); assertEquals("new", port.value)
        cleanup.clear(newer); assertNull(port.value)
    }

    @Test fun externallyReplacedIdenticalTextDoesNotBelongToOurTimer() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        cleanup.setTimeout(30); cleanup.copy("same"); runCurrent()
        port.copy("same", "external-owner")
        advanceTimeBy(30_001); runCurrent(); assertEquals("same", port.value)
    }

    @Test fun backgroundDenialKeepsExpiryWithoutPollingAndFocusRetriesIt() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        cleanup.setTimeout(30); cleanup.copy("secret"); port.readable = false; runCurrent()
        advanceTimeBy(30_001); runCurrent(); assertEquals("secret", port.value)
        advanceTimeBy(300_000); runCurrent(); assertEquals(1, port.attempts)
        port.readable = true; cleanup.onForeground()
        assertNull(port.value); assertEquals(2, port.attempts)
    }

    @Test fun disablingTimeoutCancelsAutomaticClearButKeepsManualOwnership() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        cleanup.setTimeout(30); val token = cleanup.copy("secret"); runCurrent()
        cleanup.setTimeout(null); advanceTimeBy(60_000); runCurrent(); assertEquals("secret", port.value)
        cleanup.clear(token); assertNull(port.value)
    }

    @Test fun changingTimeoutRecomputesFromCopyTimeAndExpiredEnableClearsNow() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        cleanup.setTimeout(30); cleanup.copy("secret"); runCurrent()
        advanceTimeBy(20_000); cleanup.setTimeout(60); runCurrent()
        advanceTimeBy(10_001); runCurrent(); assertEquals("secret", port.value)
        cleanup.setTimeout(30); assertNull(port.value)
        cleanup.setTimeout(null); cleanup.copy("again"); advanceTimeBy(31_000)
        cleanup.setTimeout(30); assertNull(port.value)
    }

    @Test fun failedManualClearRetriesOnFocusEvenWithTimeoutOff() = runTest {
        val port = Port(); val cleanup = cleanup(port)
        val token = cleanup.copy("secret"); port.readable = false; cleanup.clear(token)
        assertEquals("secret", port.value)
        port.readable = true; cleanup.onForeground(); assertNull(port.value)
    }
}
