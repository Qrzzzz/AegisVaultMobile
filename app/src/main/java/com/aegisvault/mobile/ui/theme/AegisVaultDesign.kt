package com.aegisvault.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AegisVaultUiPalette(
    val pageScrim: Color,
    val cardSurface: Color,
    val strongCardSurface: Color,
    val cardBorder: Color,
    val softAccentSurface: Color,
    val heroText: Color,
    val heroBody: Color,
    val encodeButton: Color,
    val encodeButtonText: Color,
    val decodeButton: Color,
    val decodeButtonText: Color,
    val chipSurface: Color,
    val chipSelectedSurface: Color,
    val chipSelectedText: Color,
    val segmentedActiveSurface: Color,
    val segmentedActiveText: Color,
    val segmentedInactiveSurface: Color,
    val segmentedInactiveText: Color,
    val resultSurface: Color,
    val resultCopySurface: Color,
    val resultCopyText: Color,
    val successSurface: Color,
    val successText: Color,
    val warningSurface: Color,
    val warningText: Color,
    val errorSurface: Color,
    val errorText: Color,
)

@Composable
fun rememberAegisVaultUiPalette(): AegisVaultUiPalette {
    return if (isSystemInDarkTheme()) {
        AegisVaultUiPalette(
            pageScrim = Color(0xAA0A0C14),
            cardSurface = Color(0xCC151923),
            strongCardSurface = Color(0xD9151A24),
            cardBorder = Color(0x335C657A),
            softAccentSurface = Color(0xCC1E2431),
            heroText = Color(0xFFF2F4FA),
            heroBody = Color(0xFFC7CCDA),
            encodeButton = Color(0x665B7A67),
            encodeButtonText = Color(0xFFE0F0E3),
            decodeButton = Color(0x666F5864),
            decodeButtonText = Color(0xFFF4DFE6),
            chipSurface = Color(0x66222A39),
            chipSelectedSurface = Color(0x88343D52),
            chipSelectedText = Color(0xFFF0F4FF),
            segmentedActiveSurface = Color(0x8836425A),
            segmentedActiveText = Color(0xFFF0F4FF),
            segmentedInactiveSurface = Color(0x55212A39),
            segmentedInactiveText = Color(0xFFBCC4D6),
            resultSurface = Color(0xAA1B2230),
            resultCopySurface = Color(0x6638445C),
            resultCopyText = Color(0xFFE8EEFF),
            successSurface = Color(0x66506A57),
            successText = Color(0xFFD7ECD9),
            warningSurface = Color(0x66705B42),
            warningText = Color(0xFFF2DEC0),
            errorSurface = Color(0x666C4E58),
            errorText = Color(0xFFF0D6DE),
        )
    } else {
        AegisVaultUiPalette(
            pageScrim = Color(0x66F5EFE7),
            cardSurface = Color(0xF5FFFCF9),
            strongCardSurface = Color(0xFAFFFDFB),
            cardBorder = Color(0x1FD4C8BC),
            softAccentSurface = Color(0xFFF2ECE3),
            heroText = Color(0xFF3F4A46),
            heroBody = Color(0xFF67716C),
            encodeButton = Color(0xFFD5E3D5),
            encodeButtonText = Color(0xFF496356),
            decodeButton = Color(0xFFE7D5D8),
            decodeButtonText = Color(0xFF74545B),
            chipSurface = Color(0xFFF2ECE3),
            chipSelectedSurface = Color(0xFFEEE5D8),
            chipSelectedText = Color(0xFF2F3532),
            segmentedActiveSurface = Color(0xFFE5EDE8),
            segmentedActiveText = Color(0xFF40564B),
            segmentedInactiveSurface = Color(0x80EDE4DA),
            segmentedInactiveText = Color(0xFF6B716D),
            resultSurface = Color(0x8CEDE4DA),
            resultCopySurface = Color(0x2E6A7F73),
            resultCopyText = Color(0xFF54685D),
            successSurface = Color(0xE6D5E3D5),
            successText = Color(0xFF496356),
            warningSurface = Color(0xFFF0E6D2),
            warningText = Color(0xFF8A7454),
            errorSurface = Color(0xE6E7D5D8),
            errorText = Color(0xFF74545B),
        )
    }
}
