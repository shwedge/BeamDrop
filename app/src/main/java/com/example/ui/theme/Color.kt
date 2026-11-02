package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Apple iOS 26 Control Center & Home Screen Clear Frosted Glass Base Palette
val CyberBlack = Color(0xFF0A1118)      // Deep Atmospheric Dark Backdrop
val DeepNavy = Color(0xFF111B24)        // Smoky Frosted Glass Sheet Base
val CardNavy = Color(0xFF1B2836)        // Translucent Clear-Smoky Glass Tint
val SurfaceElevated = Color(0xFF263849) // Elevated Control Center Pill Tint
val BorderSubtle = Color(0xFFE2E8F0)    // Crisp iOS 26 White Specular Rim

// iOS 26 Control Center & Liquid Glass Accents
val ElectricCyan = Color(0xFF38BDF8)    // iOS 26 AirDrop Sky-Blue Accent
val NeonTeal = Color(0xFF5EEAD4)        // BeamDrop / LocalSend Mint-Teal
val RadiantPurple = Color(0xFF60A5FA)   // iOS 26 Control Center Blue (#007AFF / #60A5FA)
val DeepViolet = Color(0xFF007AFF)      // Pure iOS AirDrop Blue
val SolarAmber = Color(0xFFBAE6FD)      // Frosted Crystal Specular Shimmer
val VividBlue = Color(0xFF0A84FF)       // iOS System Active Blue

// Frosted Pure-White Typography & Contrast
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFCBD5E1)
val TextTertiary = Color(0xFF94A3B8)

// Status
val SuccessGreen = Color(0xFF34D399)
val WarningOrange = Color(0xFFFBBF24)
val ErrorRed = Color(0xFFF87171)
val CoralRed = Color(0xFFFB7185)

/**
 * Dynamic Theme & Color Palette state so that changing "Theme" (e.g. "iOS 26 Clear", "Dark", "Midnight", "System")
 * or "Color" (e.g. "LocalSend", "iOS 26 Clear", "Sapphire Blue", "Royal Violet", "Emerald Glass",
 * "Sunset Rose", "Cyber Gold", "Crimson Ruby", "Arctic Cyan") immediately updates all glass cards,
 * pills, icon squircles, and atmospheric backgrounds across the entire app.
 */
data class BeamVisualTheme(
    val themeMode: String = "System",
    val colorName: String = "LocalSend",
    val isIos26ClearMode: Boolean = false,
    val primaryAccent: Color = Color(0xFF5EEAD4),
    val secondaryAccent: Color = Color(0xFF38BDF8),
    val cardBaseColor: Color = Color(0xFF18242C),
    val pillBaseColor: Color = Color(0xFF253D3B),
    val glassWhiteFrostAlpha: Float = 0.22f,
    val glassRimAlpha: Float = 0.76f,
    val backdropStops: List<Color> = listOf(
        Color(0xFF0A1618),
        Color(0xFF122B2A),
        Color(0xFF0E212B),
        Color(0xFF081016)
    )
) {
    val isClearGlass: Boolean
        get() = isIos26ClearMode
}

object ActiveBeamThemeState {
    var current by mutableStateOf(BeamVisualTheme())
        private set

    fun update(themeMode: String, colorTheme: String) {
        val isIosClear = themeMode.contains("iOS 26", ignoreCase = true) ||
            themeMode.contains("Clear", ignoreCase = true) ||
            colorTheme.contains("iOS 26", ignoreCase = true) ||
            colorTheme.contains("Clear", ignoreCase = true)
        val isMidnight = themeMode.equals("Midnight", ignoreCase = true)

        val (primary, secondary, colorBackdrop) = when {
            colorTheme.contains("iOS 26", ignoreCase = true) -> Triple(
                Color(0xFF7DD3FC),
                Color(0xFF38BDF8),
                listOf(Color(0xFF18344E), Color(0xFF264E73), Color(0xFF1B3B59), Color(0xFF0F2338))
            )
            colorTheme.contains("Sapphire", ignoreCase = true) -> Triple(
                Color(0xFF60A5FA),
                Color(0xFF2563EB),
                listOf(Color(0xFF0A2463), Color(0xFF1545B8), Color(0xFF0B256B), Color(0xFF071742))
            )
            colorTheme.contains("Violet", ignoreCase = true) -> Triple(
                Color(0xFFC084FC),
                Color(0xFF8B5CF6),
                listOf(Color(0xFF1E1042), Color(0xFF351B78), Color(0xFF1D154A), Color(0xFF0C0920))
            )
            colorTheme.contains("Emerald", ignoreCase = true) -> Triple(
                Color(0xFF34D399),
                Color(0xFF10B981),
                listOf(Color(0xFF07261E), Color(0xFF0E4435), Color(0xFF0A3026), Color(0xFF051813))
            )
            colorTheme.contains("Sunset", ignoreCase = true) || colorTheme.contains("Rose", ignoreCase = true) -> Triple(
                Color(0xFFFB7185),
                Color(0xFFF43F5E),
                listOf(Color(0xFF2E1022), Color(0xFF521B39), Color(0xFF38142C), Color(0xFF180813))
            )
            colorTheme.contains("Gold", ignoreCase = true) || colorTheme.contains("Amber", ignoreCase = true) -> Triple(
                Color(0xFFFBBF24),
                Color(0xFFF59E0B),
                listOf(Color(0xFF291D08), Color(0xFF47320E), Color(0xFF33230A), Color(0xFF171004))
            )
            colorTheme.contains("Crimson", ignoreCase = true) || colorTheme.contains("Ruby", ignoreCase = true) -> Triple(
                Color(0xFFF87171),
                Color(0xFFEF4444),
                listOf(Color(0xFF2D0C11), Color(0xFF4F141D), Color(0xFF360E15), Color(0xFF190609))
            )
            colorTheme.contains("Arctic", ignoreCase = true) || colorTheme.contains("Cyan", ignoreCase = true) -> Triple(
                Color(0xFF22D3EE),
                Color(0xFF06B6D4),
                listOf(Color(0xFF082630), Color(0xFF0E4152), Color(0xFF0B313F), Color(0xFF051820))
            )
            else -> Triple(
                Color(0xFF5EEAD4),
                Color(0xFF38BDF8),
                listOf(Color(0xFF0A1C1A), Color(0xFF13332F), Color(0xFF0E252B), Color(0xFF071216))
            )
        }

        val finalBackdrop = when {
            isIosClear -> listOf(
                Color(0xFF214363),
                Color(0xFF2E5C82),
                Color(0xFF1F4263),
                Color(0xFF142B42)
            )
            isMidnight -> listOf(
                Color(0xFF030508),
                Color(0xFF070C12),
                Color(0xFF05090E),
                Color(0xFF020305)
            )
            else -> colorBackdrop
        }

        val cardBase = when {
            isIosClear -> Color(0xFF3B668C)
            isMidnight -> Color(0xFF0A1017)
            else -> colorBackdrop[1]
        }

        val pillBase = when {
            isIosClear -> Color(0xFF47759E)
            isMidnight -> Color(0xFF121C26)
            else -> colorBackdrop[1]
        }

        current = BeamVisualTheme(
            themeMode = themeMode,
            colorName = colorTheme,
            isIos26ClearMode = isIosClear,
            primaryAccent = primary,
            secondaryAccent = secondary,
            cardBaseColor = cardBase,
            pillBaseColor = pillBase,
            glassWhiteFrostAlpha = if (isIosClear) 0.36f else if (isMidnight) 0.14f else 0.22f,
            glassRimAlpha = if (isIosClear) 0.94f else 0.76f,
            backdropStops = finalBackdrop
        )
    }
}

val LocalBeamVisualTheme = compositionLocalOf { BeamVisualTheme() }

val currentBeamTheme: BeamVisualTheme
    @Composable
    @ReadOnlyComposable
    get() = ActiveBeamThemeState.current
