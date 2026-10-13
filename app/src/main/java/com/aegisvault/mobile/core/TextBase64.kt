package com.aegisvault.mobile.core

import java.util.Base64

data class Base64DecodeOptions(val strict: Boolean = true, val ignoreAsciiWhitespace: Boolean = false)

/** Text-only decoder. Never discard arbitrary characters using the MIME decoder. */
internal object TextBase64 {
    private fun asciiWhitespace(c: Char) = c in "\t\n\r\u000c\u000b "

    // Match CryptoService's size/error order, even when strict + ignore are both true.
    // Invalid quartet shape is left to syntax validation, not estimated as decoded data.
    internal fun preflight(text: String, options: Base64DecodeOptions) {
        ResourceLimits.utf8Size(text, ResourceLimits.ENCODED)
        if (text.any { it.code > 127 }) return
        var count = 0L
        var previous = '\u0000'
        var last = '\u0000'
        for (c in text) {
            if (options.ignoreAsciiWhitespace && asciiWhitespace(c)) continue
            count++
            previous = last
            last = c
        }
        if (count == 0L || count % 4 != 0L) return
        val padding = (if (last == '=') 1 else 0) + (if (previous == '=') 1 else 0)
        ResourceLimits.size(count / 4 * 3 - padding, ResourceLimits.PLAINTEXT)
    }

    fun decode(text: String, options: Base64DecodeOptions): String {
        preflight(text, options)
        if (text.any { it.code > 127 } || (options.strict && text.any(::asciiWhitespace)))
            throw AegisVaultException(ErrorCode.BASE64_INVALID)
        val encoded = if (options.ignoreAsciiWhitespace) text.filterNot(::asciiWhitespace) else text
        // Java's decoder permits missing padding; the frozen desktop text service does not.
        if (encoded.length % 4 != 0) throw AegisVaultException(ErrorCode.BASE64_INVALID)
        val raw = try { Base64.getDecoder().decode(encoded) }
        catch (_: IllegalArgumentException) { throw AegisVaultException(ErrorCode.BASE64_INVALID) }
        val result = ProtocolJson.utf8(raw)
        ResourceLimits.utf8Size(result)
        return result
    }
}
