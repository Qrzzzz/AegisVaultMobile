package com.aegisvault.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardLogicTest {
    @Test fun delayedJobDoesNotClearNewerClipboardContent() {
        assertFalse(shouldClearClipboard(current = "new value", copied = "old value"))
    }

    @Test fun delayedJobClearsOnlyMatchingCopiedValue() {
        assertTrue(shouldClearClipboard(current = "same", copied = "same"))
    }
}
