package com.aegisvault.mobile.core

/** Provisional Mobile 1.1 limits; the JSON writer has TWO Base64 layers. */
internal object ResourceLimits {
    const val PLAINTEXT = 1_048_576
    const val ENCODED = 2_097_152
    const val METADATA = 16_384
    const val CIPHERTEXT = PLAINTEXT + 16
    const val CIPHERTEXT_ENCODED = (CIPHERTEXT * 4 + 2) / 3
    const val JSON_PAYLOAD = CIPHERTEXT_ENCODED + METADATA

    fun size(actual: Long, limit: Int) {
        if (actual > limit) throw AegisVaultException(ErrorCode.RESOURCE)
    }

    fun encryptedOutputSize(plaintextBytes: Int): Int {
        size(plaintextBytes.toLong(), PLAINTEXT)
        val inner = ((plaintextBytes.toLong() + 16) * 4 + 2) / 3
        val json = inner + METADATA
        val token = 5 + (json * 4 + 2) / 3
        size(json, JSON_PAYLOAD)
        size(token, ENCODED)
        return token.toInt()
    }

    /** Count before allocating UTF-8; reject unpaired surrogates instead of replacement. */
    fun utf8Size(text: String, limit: Int = PLAINTEXT): Int {
        size(text.length.toLong(), limit)
        var bytes = 0L
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            bytes += when {
                c < '\u0080' -> 1
                c < '\u0800' -> 2
                c.isHighSurrogate() -> {
                    if (i == text.length || !text[i].isLowSurrogate())
                        throw AegisVaultException(ErrorCode.UTF8_INVALID)
                    i++
                    4
                }
                c.isLowSurrogate() -> throw AegisVaultException(ErrorCode.UTF8_INVALID)
                else -> 3
            }
            size(bytes, limit)
        }
        return bytes.toInt()
    }

    /** Safe upper bound including unpadded encodings, before decoder allocation. */
    fun decodedSize(input: String, limit: Int) {
        size(input.length.toLong(), ENCODED)
        val padding = when {
            input.endsWith("==") -> 2
            input.endsWith("=") -> 1
            else -> 0
        }
        size(input.length.toLong() * 3 / 4 - padding, limit)
    }

    /** Bound metadata and parser nesting before JSONObject allocates its object graph. */
    fun jsonPreflight(raw: ByteArray) {
        var i = 0
        var depth = 0
        var metadata = 0L
        var ciphertextValue = false
        val key = "\"ciphertext\"".toByteArray(Charsets.US_ASCII)
        while (i < raw.size) {
            when (raw[i].toInt().toChar()) {
                '{', '[' -> { depth++; if (depth > 32) throw AegisVaultException(ErrorCode.RESOURCE) }
                '}', ']' -> depth--
                '"' -> {
                    val start = i++
                    while (i < raw.size && raw[i].toInt() != 34) {
                        if (raw[i].toInt() == 92) i++
                        i++
                    }
                    if (i >= raw.size) throw AegisVaultException(ErrorCode.PROTOCOL)
                    val length = i - start + 1
                    metadata += if (ciphertextValue && depth == 1) 2 else length.toLong()
                    var next = i + 1
                    while (next < raw.size && raw[next].toInt().toChar() in " \t\r\n") next++
                    ciphertextValue = depth == 1 && length == key.size &&
                        key.indices.all { raw[start + it] == key[it] } &&
                        next < raw.size && raw[next].toInt() == 58
                    size(metadata, METADATA)
                    i++
                    continue
                }
            }
            metadata++
            size(metadata, METADATA)
            i++
        }
    }
}
