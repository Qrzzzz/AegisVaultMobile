package com.aegisvault.mobile.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** RFC 8259 grammar, independent of Android/JVM org.json coercion and leniency. */
internal object ProtocolJson {
    fun utf8(raw: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw AegisVaultException(ErrorCode.UTF8_INVALID)
    }

    fun parse(raw: ByteArray): Map<String, Any?> {
        ResourceLimits.size(raw.size.toLong(), ResourceLimits.METADATA)
        val value = Parser(utf8(raw)).parse()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: bad()
    }

    fun canonical(value: Any?): String = when (value) {
        is Map<*, *> -> value.keys.map { it as String }.sorted().joinToString(",", "{", "}") {
            canonical(it) + ":" + canonical(value[it])
        }
        is String -> buildString {
            append('"')
            for (c in value) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
        is Int, is Long -> value.toString()
        else -> bad()
    }

    private fun bad(): Nothing = throw AegisVaultException(ErrorCode.PROTOCOL)

    private class Parser(private val text: String) {
        private var i = 0
        fun parse(): Any? {
            val result = value(0)
            space()
            if (i != text.length) bad()
            return result
        }
        private fun space() { while (i < text.length && text[i] in " \t\r\n") i++ }
        private fun take(c: Char): Boolean { space(); if (i < text.length && text[i] == c) { i++; return true }; return false }
        private fun need(c: Char) { if (!take(c)) bad() }
        private fun value(depth: Int): Any? {
            space()
            if (i == text.length) bad()
            return when (text[i]) {
                '{' -> {
                    if (depth >= 32) throw AegisVaultException(ErrorCode.RESOURCE)
                    i++
                    val map = linkedMapOf<String, Any?>()
                    if (!take('}')) {
                        do {
                            space(); val key = string()
                            if (map.containsKey(key)) bad()
                            need(':'); map[key] = value(depth + 1)
                        } while (take(','))
                        need('}')
                    }
                    map
                }
                '[' -> {
                    if (depth >= 32) throw AegisVaultException(ErrorCode.RESOURCE)
                    i++
                    val list = mutableListOf<Any?>()
                    if (!take(']')) { do { list.add(value(depth + 1)) } while (take(',')); need(']') }
                    list
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> number()
                else -> bad()
            }
        }
        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, i)) bad()
            i += word.length; return result
        }
        private fun string(): String {
            if (i >= text.length || text[i++] != '"') bad()
            val out = StringBuilder()
            while (i < text.length) {
                val c = text[i++]
                if (c == '"') {
                    val result = out.toString()
                    ResourceLimits.utf8Size(result, ResourceLimits.METADATA, ErrorCode.UTF8_INVALID)
                    return result
                }
                if (c < ' ') bad()
                if (c != '\\') out.append(c) else {
                    if (i == text.length) bad()
                    out.append(when (val e = text[i++]) {
                        '"', '\\', '/' -> e
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            if (i + 4 > text.length) bad()
                            val hex = text.substring(i, i + 4)
                            if (hex.any { it !in "0123456789abcdefABCDEF" }) bad()
                            i += 4; hex.toInt(16).toChar()
                        }
                        else -> bad()
                    })
                }
            }
            bad()
        }
        private fun number(): Any {
            val start = i
            if (text[i] == '-') i++
            if (i == text.length) bad()
            if (text[i] == '0') i++ else {
                if (text[i] !in '1'..'9') bad()
                while (i < text.length && text[i] in '0'..'9') i++
            }
            var integer = true
            if (i < text.length && text[i] == '.') {
                integer = false; i++; digits()
            }
            if (i < text.length && text[i] in "eE") {
                integer = false; i++
                if (i < text.length && text[i] in "+-") i++
                digits()
            }
            val raw = text.substring(start, i)
            if (integer) return raw.toLongOrNull() ?: bad()
            val result = raw.toDoubleOrNull() ?: bad()
            if (!result.isFinite()) bad()
            return result
        }
        private fun digits() {
            val start = i
            while (i < text.length && text[i] in '0'..'9') i++
            if (i == start) bad()
        }
    }
}
