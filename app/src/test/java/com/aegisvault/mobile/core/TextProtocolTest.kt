package com.aegisvault.mobile.core

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.SCrypt

class TextProtocolTest {
    private val fixture get() = JSONObject(javaClass.getResource("/desktop-agv1.json")!!.readText())
    private fun packageToken(raw: ByteArray) = "AGV1." + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    private fun raw() = Base64.getUrlDecoder().decode(fixture.getString("token").drop(5))
    private fun header() = raw().let { String(it, 12, ByteBuffer.wrap(it, 8, 4).int, Charsets.UTF_8) }
    private fun envelope(header: ByteArray, cipher: ByteArray = ByteArray(16)): String =
        packageToken(AegisVaultEngine.textMagic + ByteBuffer.allocate(4).putInt(header.size).array() + header + cipher)
    private fun fails(code: ErrorCode, token: String) {
        val e = assertThrows(AegisVaultException::class.java) { AegisVaultEngine.decryptText(token, fixture.getString("password")) }
        assertEquals(code, e.code)
    }
    private fun authenticated(header: String, plain: ByteArray): String {
        val key = SCrypt.generate(fixture.getString("password").toByteArray(Charsets.UTF_8), ByteArray(16) { it.toByte() }, 16384, 8, 1, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ByteArray(12) { it.toByte() }))
        cipher.updateAAD(header.toByteArray(Charsets.UTF_8))
        return envelope(header.toByteArray(Charsets.UTF_8), cipher.doFinal(plain))
    }

    @Test fun exactDesktopWriterBytesAndUnicodeDecrypt() {
        val f = fixture
        assertEquals(f.getString("plaintext"), AegisVaultEngine.decryptText(f.getString("token"), f.getString("password")).plaintext)
        val actual = AegisVaultEngine.encryptEnvelope(f.getString("plaintext"), f.getString("password"),
            ByteArray(16) { it.toByte() }, ByteArray(12) { it.toByte() }, f.getString("created_at"), AegisVaultEngine.ScryptParams(n = 16384))
        assertEquals(f.getString("token"), actual)
    }

    @Test fun originalNoncanonicalHeaderIsAadAndCannotBeReserialized() {
        val h = " \n" + header().replace(",", ", ") + "\n"
        val token = authenticated(h, "exact raw header 中文".toByteArray(Charsets.UTF_8))
        assertEquals("exact raw header 中文", AegisVaultEngine.decryptText(token, fixture.getString("password")).plaintext)
        val raw = Base64.getUrlDecoder().decode(token.drop(5))
        val end = 12 + ByteBuffer.wrap(raw, 8, 4).int
        fails(ErrorCode.AUTH, envelope(header().toByteArray(), raw.copyOfRange(end, raw.size)))
    }

    @Test fun malformedGrammarAndDuplicateKeysNeverReachKdf() {
        for (json in listOf("{\"x\":1,\"x\":2}", "{\"x\":1,\"\\u0078\":2}", "{\"a\":{\"x\":1,\"x\":2}}",
            "{\"x\":NaN}", "{\"x\":Infinity}", "{\"x\":1e999}", "{'x':1}", "{x:1}", "{\"x\":01}", "{\"x\":1,}",
            "{} {}", "[]", "{\"x\":\"line\nline\"}", "{\"x\":undefined}", "{\"x\":0x10}", "{\"x\":/*a*/1}")) {
            fails(ErrorCode.PROTOCOL, envelope(json.toByteArray()))
        }
        fails(ErrorCode.UTF8_INVALID, envelope(byteArrayOf(0x80.toByte())))
        fails(ErrorCode.UTF8_INVALID, envelope("{\"x\":\"\\ud800\"}".toByteArray()))
        fails(ErrorCode.RESOURCE, envelope(("[".repeat(33) + "]".repeat(33)).toByteArray()))
    }

    @Test fun fieldsHaveRealTypesAndSafeParameters() {
        for ((field, value) in listOf("version" to 1, "n" to 16384, "r" to 8, "p" to 1, "length" to 32)) {
            for (bad in listOf("\"$value\"", "$value.0", "true", "null", "4294967297"))
                fails(ErrorCode.PROTOCOL, envelope(header().replace("\"$field\":$value", "\"$field\":$bad").toByteArray()))
        }
        for ((old, bad) in listOf("aegisvault.text" to "aegisvault.file", "AES-256-GCM" to "AES-128-GCM", "scrypt" to "sha256"))
            fails(ErrorCode.PROTOCOL, envelope(header().replace(old, bad).toByteArray()))
        for (h in listOf(header().replace("\"version\":1", "\"version\":2"),
            header().replace("\"length\":32", "\"length\":16"),
            header().replace("AAECAwQFBgcICQoLDA0ODw==", "AAECAwQFBgcICQoLDA0O"),
            header().replace("AAECAwQFBgcICQoL", "AA=="),
            header().replace("\"created_at\":\"2026-01-02T03:04:05Z\"", "\"created_at\":null"))) fails(ErrorCode.PROTOCOL, envelope(h.toByteArray()))
        // Validator only: do not derive or execute over-budget probes.
        fails(ErrorCode.RESOURCE, envelope(header().replace("\"n\":16384", "\"n\":131072").toByteArray()))
    }

    @Test fun truncationHeaderLengthAndPayloadBudgets() {
        for (length in listOf(8, 9, 11, 12, 20, raw().size - 40)) fails(ErrorCode.PROTOCOL, packageToken(raw().copyOf(length)))
        for (length in listOf(0, -1, 65537)) {
            val bytes = raw(); ByteBuffer.wrap(bytes, 8, 4).putInt(length)
            fails(if (length == 0) ErrorCode.PROTOCOL else ErrorCode.RESOURCE, packageToken(bytes))
        }
        val bytes = raw(); bytes[7] = 2; fails(ErrorCode.PROTOCOL, packageToken(bytes))
        fails(ErrorCode.RESOURCE, envelope(header().toByteArray(), ByteArray(ResourceLimits.CIPHERTEXT + 1)))
        fails(ErrorCode.PROTOCOL, "AGV1.%%")
    }

    @Test fun authenticatedInvalidPlaintextUtf8IsRejected() {
        fails(ErrorCode.UTF8_INVALID, authenticated(header(), byteArrayOf(0xc0.toByte(), 0xaf.toByte())))
    }

    @Test fun requiredFieldsRejectNullBooleanContainersMissingAndBadBase64() {
        for (field in listOf("algorithm", "format", "created_at", "nonce", "kdf")) {
            for (value in listOf(JSONObject.NULL, true, 1, JSONArray())) {
                val h = JSONObject(header()).put(field, value)
                fails(ErrorCode.PROTOCOL, envelope(h.toString().toByteArray()))
            }
            val h = JSONObject(header()); h.remove(field)
            fails(ErrorCode.PROTOCOL, envelope(h.toString().toByteArray()))
        }
        for (field in listOf("salt", "type", "n", "r", "p", "length")) {
            val h = JSONObject(header()); h.getJSONObject("kdf").remove(field)
            fails(ErrorCode.PROTOCOL, envelope(h.toString().toByteArray()))
        }
        for (salt in listOf("", "AA==", "AAECAwQFBgcICQoLDA0ODw", "_AECAwQFBgcICQoLDA0ODw==", "AAECAwQFBgcICQoLDA0ODw==\n")) {
            val h = JSONObject(header()); h.getJSONObject("kdf").put("salt", salt)
            fails(ErrorCode.PROTOCOL, envelope(h.toString().toByteArray()))
        }
        for (timestamp in listOf("", "x".repeat(129), "nul\u0000"))
            fails(ErrorCode.PROTOCOL, envelope(JSONObject(header()).put("created_at", timestamp).toString().toByteArray()))
        for ((field, value) in listOf("n" to 16385, "n" to -1, "r" to 0, "p" to 9)) {
            val h = JSONObject(header()); h.getJSONObject("kdf").put(field, value)
            fails(ErrorCode.PROTOCOL, envelope(h.toString().toByteArray()))
        }
        // Legal header boundary independent of body; no KDF at the oversized boundary.
        val prefix = "{\"extra\":\""; val suffix = "\"}"
        val exact = prefix + "x".repeat(ResourceLimits.METADATA - prefix.length - suffix.length) + suffix
        assertEquals(ResourceLimits.METADATA, exact.length)
        ProtocolJson.parse(exact.toByteArray())
        fails(ErrorCode.RESOURCE, envelope((exact + " ").toByteArray()))
    }

    @Test fun tamperingAuthenticatedHeaderAndCiphertext() {
        val bytes = raw(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); fails(ErrorCode.AUTH, packageToken(bytes))
        val bytes2 = raw(); val h = header().replace("2026-01-02", "2026-01-03").toByteArray()
        fails(ErrorCode.AUTH, envelope(h, bytes2.copyOfRange(12 + h.size, bytes2.size)))
    }

    @Test fun realDesktopAndHistoricalMigrationExchange() {
        val corpus = JSONObject(javaClass.getResource("/interop-1.2.json")!!.readText()).getJSONArray("cases")
        val output = JSONArray()
        for (i in 0 until corpus.length()) {
            val sample = corpus.getJSONObject(i)
            val plaintext = sample.getString("plaintext"); val password = sample.getString("password")
            assertEquals(plaintext, AegisVaultEngine.decryptText(sample.getString("token"), password).plaintext)
            output.put(JSONObject().put("plaintext", plaintext).put("password", password)
                .put("token", AegisVaultEngine.encryptText(plaintext, password)))
        }
        // Artificial public data only; CI may run the frozen corpus without exporting.
        System.getenv("AEGIS_INTEROP_DIR")?.let {
            java.io.File(it, "android-tokens.json").writeText(output.toString(), Charsets.UTF_8)
        }
        assertEquals(5, output.length())
    }
}
