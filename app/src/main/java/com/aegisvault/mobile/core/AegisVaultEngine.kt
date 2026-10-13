package com.aegisvault.mobile.core

import org.bouncycastle.crypto.generators.SCrypt
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
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
    MIGRATION,
    RESOURCE,
    INTERNAL,
}

class AegisVaultException(
    val code: ErrorCode,
    override val message: String? = null,
) : Exception(message)

data class TextDecryptResult(
    val plaintext: String,
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

    internal val textMagic = byteArrayOf(65, 71, 86, 84, 69, 88, 84, 1)

    fun encryptText(plaintext: String, password: String): String = encryptEnvelope(
        plaintext, password, randomBytes(16), randomBytes(nonceSize),
        java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(), ScryptParams(),
    )

    // Deterministic inputs are internal to tests; the production entry always uses SecureRandom.
    internal fun encryptEnvelope(plaintext: String, password: String, salt: ByteArray, nonce: ByteArray,
        createdAt: String, params: ScryptParams): String {
        if (plaintext.isBlank()) throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        requirePassword(password)
        ResourceLimits.encryptedOutputSize(ResourceLimits.utf8Size(plaintext))
        validateScryptParams(params, salt.size)
        if (nonce.size != nonceSize || createdAt.isEmpty() || createdAt.codePointCount(0, createdAt.length) > 128 || '\u0000' in createdAt)
            throw AegisVaultException(ErrorCode.PROTOCOL)
        val header = ProtocolJson.canonical(mapOf(
            "algorithm" to "AES-256-GCM", "created_at" to createdAt, "format" to "aegisvault.text",
            "version" to 1, "nonce" to Base64.getEncoder().encodeToString(nonce),
            "kdf" to mapOf("type" to "scrypt", "salt" to Base64.getEncoder().encodeToString(salt),
                "n" to params.n, "r" to params.r, "p" to params.p, "length" to params.length),
        )).toByteArray(StandardCharsets.UTF_8)
        ResourceLimits.size(header.size.toLong(), ResourceLimits.METADATA)
        val ciphertext = aesGcmEncrypt(deriveScryptKey(password, salt, params), nonce,
            plaintext.toByteArray(StandardCharsets.UTF_8), header)
        val raw = java.nio.ByteBuffer.allocate(12 + header.size + ciphertext.size)
            .put(textMagic).putInt(header.size).put(header).put(ciphertext).array()
        return textPrefix + encodeUrlBase64(raw)
    }

    fun decryptText(token: String, password: String): TextDecryptResult {
        ResourceLimits.size(token.length.toLong(), ResourceLimits.ENCODED)
        val trimmed = token.trim()
        if (trimmed.isBlank()) throw AegisVaultException(ErrorCode.EMPTY_TEXT)
        if (!trimmed.startsWith(textPrefix)) throw AegisVaultException(
            if (trimmed.startsWith("AGV")) ErrorCode.PROTOCOL else ErrorCode.MIGRATION)
        val raw = decodeUrlBase64(trimmed.removePrefix(textPrefix), ResourceLimits.PACKAGE)
        if (raw.size >= 8 && raw.copyOfRange(0, 7).contentEquals(textMagic.copyOfRange(0, 7)) && raw[7] != 1.toByte())
            throw AegisVaultException(ErrorCode.PROTOCOL)
        if (raw.size < textMagic.size || !raw.copyOfRange(0, textMagic.size).contentEquals(textMagic))
            throw AegisVaultException(ErrorCode.MIGRATION)
        if (raw.size < 12) throw AegisVaultException(ErrorCode.PROTOCOL)
        val headerLength = java.nio.ByteBuffer.wrap(raw, 8, 4).int.toLong() and 0xffffffffL
        if (headerLength == 0L) throw AegisVaultException(ErrorCode.PROTOCOL)
        ResourceLimits.size(headerLength, ResourceLimits.METADATA)
        if (12L + headerLength + 16 > raw.size) throw AegisVaultException(ErrorCode.PROTOCOL)
        val end = 12 + headerLength.toInt()
        ResourceLimits.size((raw.size - end).toLong(), ResourceLimits.CIPHERTEXT)
        val headerBytes = raw.copyOfRange(12, end)
        val header = ProtocolJson.parse(headerBytes)
        if (string(header, "format") != "aegisvault.text" || integer(header, "version") != 1 ||
            string(header, "algorithm") != "AES-256-GCM") throw AegisVaultException(ErrorCode.PROTOCOL)
        val createdAt = string(header, "created_at")
        if (createdAt.isEmpty() || createdAt.codePointCount(0, createdAt.length) > 128 || '\u0000' in createdAt)
            throw AegisVaultException(ErrorCode.PROTOCOL)
        @Suppress("UNCHECKED_CAST")
        val kdf = header["kdf"] as? Map<String, Any?> ?: throw AegisVaultException(ErrorCode.PROTOCOL)
        if (string(kdf, "type") != "scrypt") throw AegisVaultException(ErrorCode.PROTOCOL)
        val salt = decodeStandardBase64(string(kdf, "salt"), 64)
        val params = ScryptParams(integer(kdf, "n"), integer(kdf, "r"), integer(kdf, "p"),
            integer(kdf, "length"), salt.size)
        validateScryptParams(params, salt.size)
        val nonce = decodeStandardBase64(string(header, "nonce"), nonceSize)
        if (nonce.size != nonceSize) throw AegisVaultException(ErrorCode.PROTOCOL)
        requirePassword(password)
        val plaintext = aesGcmDecrypt(deriveScryptKey(password, salt, params), nonce,
            raw.copyOfRange(end, raw.size), headerBytes)
        val text = ProtocolJson.utf8(plaintext)
        ResourceLimits.utf8Size(text)
        return TextDecryptResult(text)
    }

    private fun string(map: Map<String, Any?>, field: String): String =
        map[field] as? String ?: throw AegisVaultException(ErrorCode.PROTOCOL)

    private fun integer(map: Map<String, Any?>, field: String): Int {
        val value = map[field] as? Long ?: throw AegisVaultException(ErrorCode.PROTOCOL)
        if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) throw AegisVaultException(ErrorCode.PROTOCOL)
        return value.toInt()
    }

    private fun decodeStandardBase64(input: String, limit: Int): ByteArray {
        ResourceLimits.decodedSize(input, limit)
        if (input.length % 4 != 0) throw AegisVaultException(ErrorCode.PROTOCOL)
        return try { Base64.getDecoder().decode(input) }
        catch (_: IllegalArgumentException) { throw AegisVaultException(ErrorCode.PROTOCOL) }
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
