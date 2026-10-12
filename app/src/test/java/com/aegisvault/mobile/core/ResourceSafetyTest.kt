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

    private fun wrap(payload: JSONObject) = "AGV1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray())
    private fun fixture(): JSONObject = JSONObject(javaClass.getResource("/android-historical.json")!!.readText()).getJSONObject("modern")
    private fun payload(): JSONObject = JSONObject(String(Base64.getUrlDecoder().decode(fixture().getString("token").removePrefix("AGV1."))))

    @Test fun rejectsCoercedFieldsBeforeKdf() {
        for (name in listOf("n", "r", "p", "length", "salt_len")) {
            val valid = payload().getJSONObject("kdf").getInt(name)
            for (value in listOf<Any>(valid.toString(), valid + 0.5, true, JSONObject.NULL, valid.toLong() + 4294967296L)) {
                val p = payload(); p.getJSONObject("kdf").put(name, value)
                fails(ErrorCode.PROTOCOL) { AegisVaultEngine.decryptText(wrap(p), "audit-password") }
            }
        }
        val p = payload().put("salt", Base64.getUrlEncoder().encodeToString(ByteArray(15)))
        fails(ErrorCode.PROTOCOL) { AegisVaultEngine.decryptText(wrap(p), "audit-password") }
        fails(ErrorCode.PROTOCOL) { AegisVaultEngine.decryptText(wrap(payload().put("v", "1")), "audit-password") }
        val fractionalJson = payload().toString().replace("\"r\":8", "\"r\":8.0")
        val fractionalToken = "AGV1." + Base64.getUrlEncoder().withoutPadding().encodeToString(fractionalJson.toByteArray())
        fails(ErrorCode.PROTOCOL) { AegisVaultEngine.decryptText(fractionalToken, "audit-password") }
    }

    @Test fun historicalAndroidSamplesRemainReadable() {
        val samples = JSONObject(javaClass.getResource("/android-historical.json")!!.readText())
        for (name in listOf("modern", "legacy", "ak")) {
            val sample = samples.getJSONObject(name)
            val result = AegisVaultEngine.decryptText(sample.getString("token"), sample.getString("password"))
            assertEquals(sample.getString("plaintext"), result.plaintext)
            assertEquals(name == "ak", result.usedLegacyAk)
        }
    }

    @Test fun desktopFormatStillExplicitlyIncompatible() {
        val sample = JSONObject(javaClass.getResource("/desktop-agv1.json")!!.readText())
        fails(ErrorCode.PROTOCOL) { AegisVaultEngine.decryptText(sample.getString("token"), sample.getString("password")) }
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
        assertEquals(1_886_015, ResourceLimits.encryptedOutputSize(ResourceLimits.PLAINTEXT))
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
        fails(ErrorCode.RESOURCE) { AegisVaultEngine.decryptText(wrap(payload().put("extra", "a".repeat(ResourceLimits.METADATA))), "p") }
        fails(ErrorCode.RESOURCE) { ResourceLimits.jsonPreflight(("[".repeat(33) + "]".repeat(33)).toByteArray()) }
        fails(ErrorCode.UTF8_INVALID) { AegisVaultEngine.encodeText("\uD800") }
        fails(ErrorCode.UTF8_INVALID) { AegisVaultEngine.encryptText("a", "\uDC00") }
    }
}
