package com.aegisvault.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardLogicTest {
    @Test fun currentEqualsCopiedReturnsTrue() { assertTrue(shouldClearClipboard(current = "same", copied = "same")) }
    @Test fun currentDifferentCopiedReturnsFalse() { assertFalse(shouldClearClipboard(current = "new value", copied = "old value")) }
    @Test fun currentNullReturnsFalse() { assertFalse(shouldClearClipboard(current = null, copied = "old value")) }
    @Test fun copiedEmptyReturnsFalse() { assertFalse(shouldClearClipboard(current = "", copied = "")) }
}
