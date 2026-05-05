package com.aegisvault.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AegisVaultEngineTest {
    @Test
    fun encryptTextDecryptTextRoundTripsModernToken() {
        val token = AegisVaultEngine.encryptText("hello secure world", "correct horse battery staple")

        assertTrue(token.startsWith("AGV1."))
        assertEquals(
            "hello secure world",
            AegisVaultEngine.decryptText(token, "correct horse battery staple").plaintext,
        )
        assertFalse(AegisVaultEngine.decryptText(token, "correct horse battery staple").usedLegacyAk)
    }

    @Test
    fun base64EncodeDecodeRoundTripsUtf8Text() {
        val encoded = AegisVaultEngine.encodeText("AegisVault 123")

        assertEquals("AegisVault 123", AegisVaultEngine.decodeText(encoded))
    }

    @Test
    fun decryptTextWithWrongPasswordThrowsAuthError() {
        val token = AegisVaultEngine.encryptText("secret", "right-password")

        try {
            AegisVaultEngine.decryptText(token, "wrong-password")
        } catch (error: AegisVaultException) {
            assertEquals(ErrorCode.AUTH, error.code)
            return
        }

        throw AssertionError("Expected AegisVaultException")
    }
}
