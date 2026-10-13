package com.aegisvault.mobile.core

/** Mobile device policy; formal AGV1 uses one Base64 layer and a separate binary header. */
internal object ResourceLimits {
    const val PLAINTEXT = 1_048_576
    const val ENCODED = 2_097_152
    const val METADATA = 65_536
    const val CIPHERTEXT = PLAINTEXT + 16
    const val PACKAGE = 12 + METADATA + CIPHERTEXT

    fun size(actual: Long, limit: Int) {
        if (actual > limit) throw AegisVaultException(ErrorCode.RESOURCE)
    }

    fun encryptedOutputSize(plaintextBytes: Int): Int {
        size(plaintextBytes.toLong(), PLAINTEXT)
        val binary = 12L + METADATA + plaintextBytes + 16
        val token = 5 + (binary * 4 + 2) / 3
        size(binary, PACKAGE)
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

}
