package com.aegisvault.mobile.core

import org.bouncycastle.crypto.generators.SCrypt
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
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
    UTF8_INVALID,
    PROTOCOL,
    RESOURCE,
    INTERNAL,
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
        ResourceLimits.encryptedOutputSize(ResourceLimits.utf8Size(plaintext))
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
        ResourceLimits.size(raw.size.toLong(), ResourceLimits.JSON_PAYLOAD)
        val token = textPrefix + encodeUrlBase64(raw)
        ResourceLimits.size(token.length.toLong(), ResourceLimits.ENCODED)
        return token
    }

    fun decryptText(token: String, password: String): TextDecryptResult {
        ResourceLimits.size(token.length.toLong(), ResourceLimits.ENCODED)
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
        val bytes = ResourceLimits.utf8Size(text)
        ResourceLimits.size((bytes.toLong() + 2) / 3 * 4, ResourceLimits.ENCODED)
        return Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun decodeText(text: String): String {
        ResourceLimits.size(text.length.toLong(), ResourceLimits.ENCODED)
        if (text.isBlank()) {
            throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        }
        val token = text.trim()
        ResourceLimits.decodedSize(token, ResourceLimits.PLAINTEXT)
        val decoded = try {
            Base64.getDecoder().decode(token)
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.BASE64_INVALID)
        }
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(decoded)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw AegisVaultException(ErrorCode.UTF8_INVALID)
        }
    }

    private fun decryptModernText(token: String, password: String): String {
        requirePassword(password)
        val payloadRaw = decodeUrlBase64(token.removePrefix(textPrefix), ResourceLimits.JSON_PAYLOAD)
        ResourceLimits.jsonPreflight(payloadRaw)
        try {
            val payloadText = try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(payloadRaw)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val payload = JSONObject(payloadText)
            if (strictInt(payload, "v") != 1 || strictString(payload, "kind") != "text" ||
                strictString(payload, "alg") != "AES-256-GCM") {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val kdf = payload.getJSONObject("kdf")
            if (strictString(kdf, "name") != "scrypt") {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val params = ScryptParams(
                n = strictInt(kdf, "n"),
                r = strictInt(kdf, "r"),
                p = strictInt(kdf, "p"),
                length = strictInt(kdf, "length"),
                saltLength = strictInt(kdf, "salt_len"),
            )
            validateScryptParams(params)
            val ciphertextToken = strictString(payload, "ciphertext")
            ResourceLimits.size(payloadRaw.size.toLong() - ciphertextToken.length, ResourceLimits.METADATA)
            val salt = decodeUrlBase64(strictString(payload, "salt"), 64)
            validateScryptParams(params, salt.size)
            val nonce = decodeUrlBase64(strictString(payload, "nonce"), nonceSize)
            val ciphertext = decodeUrlBase64(ciphertextToken, ResourceLimits.CIPHERTEXT)
            if (nonce.size != nonceSize || ciphertext.size < 16) {
                throw AegisVaultException(ErrorCode.PROTOCOL)
            }
            val key = deriveScryptKey(password, salt, params)
            val plaintext = aesGcmDecrypt(key, nonce, ciphertext, null)
            ResourceLimits.size(plaintext.size.toLong(), ResourceLimits.PLAINTEXT)
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

        ResourceLimits.utf8Size(passwordToUse)
        ResourceLimits.decodedSize(rawToken, ResourceLimits.CIPHERTEXT + nonceSize)
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
        ResourceLimits.utf8Size(password)
    }

    private fun deriveScryptKey(password: String, salt: ByteArray, params: ScryptParams): ByteArray {
        validateScryptParams(params, salt.size)
        return try {
            SCrypt.generate(
                password.toByteArray(StandardCharsets.UTF_8), salt,
                params.n, params.r, params.p, params.length,
            )
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
    }

    internal fun validateScryptParams(params: ScryptParams, actualSaltLength: Int = params.saltLength) {
        val validPowerOfTwo = params.n >= (1 shl 14) && params.n <= (1 shl 20) && (params.n and (params.n - 1) == 0)
        if (!validPowerOfTwo || params.r !in 1..16 || params.p !in 1..8 || params.length != 32 ||
            params.saltLength !in 16..64 || actualSaltLength !in 16..64 || actualSaltLength != params.saltLength) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
        val memory = 128L * params.n * params.r
        val work = params.n.toLong() * params.r * params.p
        if (memory > 64L * 1024 * 1024 || work > 4_194_304L)
            throw AegisVaultException(ErrorCode.RESOURCE)
    }

    private fun strictInt(json: JSONObject, name: String): Int {
        val value = json.get(name)
        if (value !is Int) throw AegisVaultException(ErrorCode.PROTOCOL)
        return value
    }

    private fun strictString(json: JSONObject, name: String): String {
        val value = json.get(name)
        if (value !is String) throw AegisVaultException(ErrorCode.PROTOCOL)
        return value
    }

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray = cryptoOperation {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSizeBits, nonce))
        aad?.let(cipher::updateAAD)
        cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray?): ByteArray = cryptoOperation {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSizeBits, nonce))
        aad?.let(cipher::updateAAD)
        cipher.doFinal(ciphertext)
    }

    private inline fun cryptoOperation(block: () -> ByteArray): ByteArray =
        try {
            block()
        } catch (_: AEADBadTagException) {
            throw AegisVaultException(ErrorCode.AUTH)
        } catch (_: javax.crypto.BadPaddingException) {
            throw AegisVaultException(ErrorCode.AUTH)
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        } catch (_: java.security.GeneralSecurityException) {
            throw AegisVaultException(ErrorCode.INTERNAL)
        }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private fun encodeUrlBase64(input: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(input)

    private fun decodeUrlBase64(input: String, limit: Int): ByteArray {
        ResourceLimits.decodedSize(input, limit)
        val padded = input.padEnd(input.length + (4 - input.length % 4) % 4, '=')
        return try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            throw AegisVaultException(ErrorCode.PROTOCOL)
        }
    }
}
