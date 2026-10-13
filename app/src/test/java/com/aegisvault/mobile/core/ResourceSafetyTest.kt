package com.aegisvault.mobile.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ResourceSafetyTest {
    private fun fails(code: ErrorCode, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: AegisVaultException) { assertEquals(code, e.code) }
    }

    @Test fun kdfBoundariesOnlyValidateNeverDerive() {
        val base = AegisVaultEngine.ScryptParams()
        for (salt in listOf(16, 64)) AegisVaultEngine.validateScryptParams(base.copy(saltLength = salt), salt)
        for (salt in listOf(0, 15, 65)) fails(ErrorCode.PROTOCOL) { AegisVaultEngine.validateScryptParams(base, salt) }
        for (length in listOf(16, 24, 64)) fails(ErrorCode.PROTOCOL) { AegisVaultEngine.validateScryptParams(base.copy(length = length)) }
        AegisVaultEngine.validateScryptParams(base.copy(n = 65536, r = 8, p = 8)) // 64 MiB / 4194304
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.validateScryptParams(base.copy(n = 65536, r = 16)) }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.validateScryptParams(base.copy(n = 131072, r = 8)) }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.validateScryptParams(base.copy(n = 1 shl 20, r = 16, p = 8)) }
        for (n in listOf(0, -1, Int.MAX_VALUE, 16385)) fails(ErrorCode.PROTOCOL) { AegisVaultEngine.validateScryptParams(base.copy(n = n)) }
    }

    @Test fun historicalAndroidSamplesRequireExplicitMigration() {
        val samples = JSONObject(javaClass.getResource("/android-historical.json")!!.readText())
        for (name in listOf("modern", "legacy", "ak")) {
            val sample = samples.getJSONObject(name)
            fails(ErrorCode.MIGRATION) { AegisVaultEngine.decryptText(sample.getString("token"), sample.getString("password")) }
        }
    }

    @Test fun desktopFixtureNowReadable() {
        val sample = JSONObject(javaClass.getResource("/desktop-agv1.json")!!.readText())
        assertEquals(sample.getString("plaintext"), AegisVaultEngine.decryptText(sample.getString("token"), sample.getString("password")).plaintext)
    }

    @Test fun thirtyKiBAndUnicodeRoundTrip() {
        for (text in listOf("x".repeat(30 * 1024), "中文".repeat(6000), "😀".repeat(8000))) {
            val token = AegisVaultEngine.encryptText(text, "safe-test")
            assertEquals(text, AegisVaultEngine.decryptText(token, "safe-test").plaintext)
            assertTrue(token.length <= ResourceLimits.ENCODED)
            assertEquals(text, AegisVaultEngine.decodeText(AegisVaultEngine.encodeText(text)))
        }
    }

    @Test fun exactSafePlaintextBoundaryAndOutputBudget() {
        val text = "x".repeat(ResourceLimits.PLAINTEXT)
        assertEquals(1_485_525, ResourceLimits.encryptedOutputSize(ResourceLimits.PLAINTEXT))
        val token = AegisVaultEngine.encryptText(text, "safe-test")
        assertTrue(token.length <= ResourceLimits.encryptedOutputSize(ResourceLimits.PLAINTEXT))
        assertEquals(text, AegisVaultEngine.decryptText(token, "safe-test").plaintext)
        assertEquals(text, AegisVaultEngine.decodeText(AegisVaultEngine.encodeText(text)))
        assertEquals(ResourceLimits.PLAINTEXT, ResourceLimits.utf8Size("中".repeat(349525) + "a"))
        assertEquals(ResourceLimits.PLAINTEXT, ResourceLimits.utf8Size("😀".repeat(262144)))
    }

    @Test fun oversizedInputsFailBeforeKdfOrDecoding() {
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.encryptText("a".repeat(ResourceLimits.PLAINTEXT + 1), "p") }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.encodeText("中".repeat(349526)) }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.encodeText("😀".repeat(262145)) }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.decodeText("A".repeat(ResourceLimits.ENCODED + 1)) }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.decodeText("A".repeat(1398104)) }
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.decryptText("AGV1." + "A".repeat(ResourceLimits.ENCODED), "p") }
        fails(ErrorCode.RESOURCE) { ProtocolJson.parse(ByteArray(ResourceLimits.METADATA + 1)) }
        fails(ErrorCode.RESOURCE) { ProtocolJson.parse(("[".repeat(33) + "]".repeat(33)).toByteArray()) }
        fails(ErrorCode.UTF8_INVALID) { AegisVaultEngine.encodeText("\uD800") }
        fails(ErrorCode.UTF8_INVALID) { AegisVaultEngine.encryptText("a", "\uDC00") }
    }
}
