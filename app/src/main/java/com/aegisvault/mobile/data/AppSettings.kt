package com.aegisvault.mobile.data

enum class LanguageOption { SYSTEM, ENGLISH, ZH_CN }
enum class ThemeOption { SYSTEM, LIGHT, DARK }
enum class ClipboardAutoClear(val seconds: Long?) { OFF(null), S30(30), S60(60), S300(300) }

data class AppSettings(
    val language: LanguageOption = LanguageOption.SYSTEM,
    val theme: ThemeOption = ThemeOption.SYSTEM,
    val protectScreen: Boolean = false,
    val autoClearOnLeave: Boolean = false,
    val clipboardAutoClear: ClipboardAutoClear = ClipboardAutoClear.OFF,
)
