package com.aegisvault.mobile.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class TextSemanticsTest {
    private fun text(case: JSONObject): String = if (case.has("text")) case.getString("text")
        else case.getString("repeat").repeat(case.getInt("count"))

    private fun category(code: ErrorCode): String = when (code) {
        ErrorCode.INPUT_INVALID -> "INPUT_INVALID"
        ErrorCode.EMPTY_PASSWORD -> "PASSWORD_REQUIRED"
        ErrorCode.BASE64_INVALID, ErrorCode.UTF8_INVALID -> "ENCODING_INVALID"
        ErrorCode.RESOURCE -> "RESOURCE"
        else -> throw AssertionError("Unexpected domain error $code")
    }

    private fun normalized(value: String): JSONObject {
        val result = JSONObject().put("kind", "ok")
        if (value.length <= 10000) return result.put("text", value)
        val bytes = value.toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return result.put("utf8_bytes", bytes.size).put("sha256", hash)
    }

    @Test fun actualDesktopCorpusMatchesAllFourModesAndFullInputChain() {
        val cases = JSONObject(javaClass.getResource("/text-semantics-1.3.json")!!.readText()).getJSONArray("cases")
        val output = JSONArray()
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i); val input = text(case)
            val expected = case.getJSONObject("desktop")
            val actual = try {
                when (case.getString("op")) {
                    "encode" -> normalized(AegisVaultEngine.encodeText(input))
                    "decode" -> normalized(AegisVaultEngine.decodeText(input, case.getBoolean("strict"), case.getBoolean("ignore")))
                    "encrypt" -> {
                        val token = AegisVaultEngine.encryptText(input, case.getString("password"))
                        assertEquals(input, AegisVaultEngine.decryptText(token, case.getString("password")).plaintext)
                        assertEquals(input, AegisVaultEngine.decryptText(expected.getString("token"), case.getString("password")).plaintext)
                        JSONObject().put("kind", "ok").put("text", input).put("token", token)
                    }
                    else -> throw AssertionError("Unknown corpus operation")
                }
            } catch (e: AegisVaultException) {
                JSONObject().put("kind", "error").put("category", category(e.code))
            }
            assertEquals("case $i", expected.getString("kind"), actual.getString("kind"))
            for (key in expected.keys()) if (key !in listOf("desktop_code", "token")) {
                assertEquals("case $i field $key", expected.get(key), actual.get(key))
            }
            output.put(actual.put("id", case.getInt("id")))
        }
        assertEquals(783, output.length())
        System.getenv("AEGIS_INTEROP_DIR")?.let {
            java.io.File(it, "android-semantics.json").writeText(output.toString(), Charsets.UTF_8)
        }
    }

    @Test fun invalidInputAndInvalidDecodedUtf8StayDistinct() {
        for (bad in listOf("\uD800", "\uDC00", "x\uD800y", "\uD800\uD800")) {
            for (op in listOf<() -> Unit>(
                { AegisVaultEngine.encodeText(bad) }, { AegisVaultEngine.encryptText(bad, "p") },
                { AegisVaultEngine.encryptText("x", bad) }, { AegisVaultEngine.decryptText("AGV1.$bad", "p") },
                { AegisVaultEngine.decodeText(bad, false, true) },
            )) assertEquals(ErrorCode.INPUT_INVALID, assertThrows(AegisVaultException::class.java) { op() }.code)
        }
        assertEquals(ErrorCode.UTF8_INVALID, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decodeText("gA==") }.code)
        assertEquals(ErrorCode.BASE64_INVALID, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decodeText(" YQ== ") }.code)
        val token = AegisVaultEngine.encryptText("safe", "p")
        assertEquals(ErrorCode.INPUT_INVALID, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decryptText(token, "\uD800") }.code)
    }

    @Test fun whitespaceAndUnicodePasswordsAreExactAndTokenEdgesFollowDesktop() {
        val migrated = JSONObject(javaClass.getResource("/text-semantics-1.3.json")!!.readText()).getJSONObject("migration_whitespace")
        assertEquals(migrated.getString("plaintext"), AegisVaultEngine.decryptText(migrated.getString("token"), migrated.getString("password")).plaintext)
        for (password in listOf(" ", "\t\n", " edge ", "中文🔐", "é", "e\u0301")) {
            val token = AegisVaultEngine.encryptText("  exact 中文🔐  ", password)
            assertEquals("  exact 中文🔐  ", AegisVaultEngine.decryptText("\u0085\u00A0$token\u3000\u001f", password).plaintext)
            val wrong = if (password.endsWith(" ")) password + "x" else password + " "
            assertEquals(ErrorCode.AUTH, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decryptText(token, wrong) }.code)
        }
    }

    @Test fun modeBudgetOrderAndAndroidPolicyRemainBounded() {
        val exact = "a".repeat(ResourceLimits.PLAINTEXT)
        assertEquals(exact, AegisVaultEngine.decodeText(AegisVaultEngine.encodeText(exact)))
        val encodedOverBody = java.util.Base64.getEncoder().encodeToString(ByteArray(ResourceLimits.PLAINTEXT + 1) { 97 })
        assertEquals(ErrorCode.RESOURCE, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decodeText(encodedOverBody) }.code)
        val ignored = " " + encodedOverBody
        assertEquals(ErrorCode.RESOURCE, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decodeText(ignored, true, true) }.code)
        val shapeInvalid = "A".repeat(1398103)
        TextBase64.preflight(shapeInvalid, Base64DecodeOptions())
        assertEquals(ErrorCode.BASE64_INVALID, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decodeText(shapeInvalid) }.code)
        assertEquals(ErrorCode.RESOURCE, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.encryptText("", "p".repeat(ResourceLimits.PLAINTEXT + 1)) }.code)
        assertEquals(ErrorCode.RESOURCE, assertThrows(AegisVaultException::class.java) { AegisVaultEngine.encodeText(exact + "a") }.code)
    }
}
