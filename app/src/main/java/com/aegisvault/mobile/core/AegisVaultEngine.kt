package com.aegisvault.mobile.core

import org.bouncycastle.crypto.generators.SCrypt
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class ErrorCode {
    EMPTY_TEXT,
    EMPTY_PASSWORD,
    AUTH,
    BASE64_INVALID,
    PROTOCOL,
}

class AegisVaultException(
    val code: ErrorCode,
    override val message: String? = null,
) : Exception(message)

data class TextDecryptResult(
    val plaintext: String,
    val usedLegacyAk: Boolean,
)

object AegisVaultEngine {
    private const val textPrefix = "AGV1."
    private const val nonceSize = 12
    private const val tagSizeBits = 128
    private const val headerLimit = 32 * 1024

    private val secureRandom = SecureRandom()

    data class ScryptParams(
        val n: Int = 1 shl 15,
        val r: Int = 8,
        val p: Int = 1,
        val length: Int = 32,
        val saltLength: Int = 16,
    )

    fun encryptText(plaintext: String, password: String): String {
        if (plaintext.isBlank()) {
            throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        }
        requirePassword(password)
        val params = ScryptParams()
        val salt = randomBytes(params.saltLength)
        val nonce = randomBytes(nonceSize)
        val key = deriveScryptKey(password, salt, params)
        val ciphertext = aesGcmEncrypt(key, nonce, plaintext.toByteArray(StandardCharsets.UTF_8), null)
        val payload = JSONObject()
            .put("v", 1)
            .put("kind", "text")
            .put("alg", "AES-256-GCM")
            .put(
                "kdf",
                JSONObject()
                    .put("name", "scrypt")
                    .put("n", params.n)
                    .put("r", params.r)
                    .put("p", params.p)
                    .put("length", params.length)
                    .put("salt_len", params.saltLength),
            )
            .put("salt", encodeUrlBase64(salt))
            .put("nonce", encodeUrlBase64(nonce))
            .put("ciphertext", encodeUrlBase64(ciphertext))

        val raw = payload.toString().toByteArray(StandardCharsets.UTF_8)
        if (raw.size > headerLimit) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
        return textPrefix + encodeUrlBase64(raw)
    }

    fun decryptText(token: String, password: String): TextDecryptResult {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        }
        return if (trimmed.startsWith(textPrefix)) {
            TextDecryptResult(plaintext = decryptModernText(trimmed, password), usedLegacyAk = false)
        } else {
            decryptLegacyText(trimmed, password)
        }
    }

    fun encodeText(text: String): String {
        if (text.isBlank()) {
            throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        }
        return Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun decodeText(text: String): String {
        if (text.isBlank()) {
            throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        }
        val decoded = try {
            Base64.getDecoder().decode(text.trim())
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.BASE64_INVALID)
        }
        return try {
            decoded.toString(StandardCharsets.UTF_8)
        } catch (_: Exception) {
            throw AegisVaultException(ErrorCode.BASE64_INVALID)
        }
    }

    private fun decryptModernText(token: String, password: String): String {
        requirePassword(password)
        val payloadRaw = decodeUrlBase64(token.removePrefix(textPrefix))
        if (payloadRaw.size > headerLimit) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
        try {
            val payload = JSONObject(payloadRaw.toString(StandardCharsets.UTF_8))
            if (payload.optInt("v") != 1 || payload.optString("kind") != "text") {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val kdf = payload.getJSONObject("kdf")
            if (kdf.optString("name") != "scrypt") {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val params = ScryptParams(
                n = kdf.getInt("n"),
                r = kdf.getInt("r"),
                p = kdf.getInt("p"),
                length = kdf.optInt("length", 32),
                saltLength = kdf.optInt("salt_len", 16),
            )
            validateScryptParams(params)
            val salt = decodeUrlBase64(payload.getString("salt"))
            val nonce = decodeUrlBase64(payload.getString("nonce"))
            val ciphertext = decodeUrlBase64(payload.getString("ciphertext"))
            if (nonce.size != nonceSize) {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val key = deriveScryptKey(password, salt, params)
            val plaintext = aesGcmDecrypt(key, nonce, ciphertext, null)
            return plaintext.toString(StandardCharsets.UTF_8)
        } catch (_: JSONException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
    }

    private fun decryptLegacyText(token: String, password: String): TextDecryptResult {
        var rawToken = token
        var passwordToUse = password
        var usedLegacyAk = false

        if (rawToken.startsWith("AK#")) {
            val parts = rawToken.split("#", limit = 3)
            if (parts.size != 3 || parts[1].isBlank() || parts[2].isBlank()) {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            passwordToUse = parts[1]
            rawToken = parts[2]
            usedLegacyAk = true
        } else {
            requirePassword(passwordToUse)
        }

        val raw = try {
            Base64.getDecoder().decode(rawToken)
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
        if (raw.size < nonceSize + 16) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
        val nonce = raw.copyOfRange(0, nonceSize)
        val ciphertext = raw.copyOfRange(nonceSize, raw.size)
        val key = sha256(passwordToUse.toByteArray(StandardCharsets.UTF_8))
        val plaintext = aesGcmDecrypt(key, nonce, ciphertext, null)
        return TextDecryptResult(
            plaintext = plaintext.toString(StandardCharsets.UTF_8),
            usedLegacyAk = usedLegacyAk,
        )
    }

    private fun requirePassword(password: String) {
        if (password.isBlank()) {
            throw AegisVaultException(ErrorCode.EMPTY_PASSWORD)
        }
    }

    private fun deriveScryptKey(password: String, salt: ByteArray, params: ScryptParams): ByteArray {
        validateScryptParams(params)
        return SCrypt.generate(
            password.toByteArray(StandardCharsets.UTF_8),
            salt,
            params.n,
            params.r,
            params.p,
            params.length,
        )
    }

    private fun validateScryptParams(params: ScryptParams) {
        val validPowerOfTwo = params.n >= (1 shl 14) && params.n <= (1 shl 20) && (params.n and (params.n - 1) == 0)
        if (!validPowerOfTwo || params.r !in 1..16 || params.p !in 1..8 || params.length !in 16..64 || params.saltLength !in 16..64) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
    }

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSizeBits, nonce))
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSizeBits, nonce))
        aad?.let(cipher::updateAAD)
        return try {
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw AegisVaultException(ErrorCode.AUTH)
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private fun encodeUrlBase64(input: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(input)

    private fun decodeUrlBase64(input: String): ByteArray {
        val padded = input.padEnd(input.length + (4 - input.length % 4) % 4, '=')
        return try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
    }
}
