package org.parkjw.apps.oot.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LightBackground = Color(0xFFF7F7F4)
val LightOnBackground = Color(0xFF26251E)
val LightSurface = Color(0xFFF7F7F4)
val LightOnSurface = Color(0xFF26251E)
val LightSurfaceVariant = Color(0xFFE6E5E0)
val LightOnSurfaceVariant = Color(0xFF5A5852)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFAFAF7)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFF2F1EC)
val LightSurfaceContainerHighest = Color(0xFFEFEEE8)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFE6E5E0)
val LightSurfaceTint = Color(0xFFF7F7F4)
val LightPrimary = Color(0xFFF54E00)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFDE3D6)
val LightOnPrimaryContainer = Color(0xFF5A1C00)
val LightSecondary = Color(0xFF26251E)
val LightOnSecondary = Color(0xFFF7F7F4)
val LightSecondaryContainer = Color(0xFFE6E5E0)
val LightOnSecondaryContainer = Color(0xFF26251E)
val LightTertiary = Color(0xFF26251E)
val LightOnTertiary = Color(0xFFF7F7F4)
val LightTertiaryContainer = Color(0xFFE6E5E0)
val LightOnTertiaryContainer = Color(0xFF26251E)
val LightOutline = Color(0xFF807D72)
val LightOutlineVariant = Color(0xFFE6E5E0)
val LightError = Color(0xFFCF2D56)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFBE1E7)
val LightOnErrorContainer = Color(0xFF5C0A22)
val LightInverseSurface = Color(0xFF26251E)
val LightInverseOnSurface = Color(0xFFF7F7F4)
val LightInversePrimary = Color(0xFFFF8A50)
val LightScrim = Color(0xFF000000)
val DarkBackground = Color(0xFF1B1A16)
val DarkOnBackground = Color(0xFFEDEBE4)
val DarkSurface = Color(0xFF1B1A16)
val DarkOnSurface = Color(0xFFEDEBE4)
val DarkSurfaceVariant = Color(0xFF36342D)
val DarkOnSurfaceVariant = Color(0xFFB6B2A7)
val DarkSurfaceContainerLowest = Color(0xFF141310)
val DarkSurfaceContainerLow = Color(0xFF1F1E1A)
val DarkSurfaceContainer = Color(0xFF23221D)
val DarkSurfaceContainerHigh = Color(0xFF2C2A24)
val DarkSurfaceContainerHighest = Color(0xFF36342D)
val DarkSurfaceBright = Color(0xFF3A3831)
val DarkSurfaceDim = Color(0xFF1B1A16)
val DarkSurfaceTint = Color(0xFF1B1A16)
val DarkPrimary = Color(0xFFFF8A50)
val DarkOnPrimary = Color(0xFF2A1000)
val DarkPrimaryContainer = Color(0xFF4A1E08)
val DarkOnPrimaryContainer = Color(0xFFFFDCCB)
val DarkSecondary = Color(0xFFEDEBE4)
val DarkOnSecondary = Color(0xFF1B1A16)
val DarkSecondaryContainer = Color(0xFF36342D)
val DarkOnSecondaryContainer = Color(0xFFEDEBE4)
val DarkTertiary = Color(0xFFEDEBE4)
val DarkOnTertiary = Color(0xFF1B1A16)
val DarkTertiaryContainer = Color(0xFF36342D)
val DarkOnTertiaryContainer = Color(0xFFEDEBE4)
val DarkOutline = Color(0xFF8F8B80)
val DarkOutlineVariant = Color(0xFF36342D)
val DarkError = Color(0xFFFF8FA8)
val DarkOnError = Color(0xFF4A0A1E)
val DarkErrorContainer = Color(0xFF6B1A33)
val DarkOnErrorContainer = Color(0xFFFFD9E1)
val DarkInverseSurface = Color(0xFFEDEBE4)
val DarkInverseOnSurface = Color(0xFF1B1A16)
val DarkInversePrimary = Color(0xFFF54E00)
val DarkScrim = Color(0xFF000000)

val LightHairline = Color(0xFFE6E5E0)
val LightHairlineStrong = Color(0xFFCFCDC4)
val LightMuted = Color(0xFF807D72)
val LightInkButton = Color(0xFF26251E)
val LightOnInkButton = Color(0xFFF7F7F4)
val LightSuccess = Color(0xFF1F8A65)
val DarkHairline = Color(0xFF36342D)
val DarkHairlineStrong = Color(0xFF4A473F)
val DarkMuted = Color(0xFF8F8B80)
val DarkInkButton = Color(0xFFEDEBE4)
val DarkOnInkButton = Color(0xFF1B1A16)
val DarkSuccess = Color(0xFF5CC49A)

data class OotExtendedColors(
    val hairline: Color,
    val hairlineStrong: Color,
    val muted: Color,
    val inkButton: Color,
    val onInkButton: Color,
    val success: Color,
)

val LightOotColors = OotExtendedColors(
    hairline = LightHairline,
    hairlineStrong = LightHairlineStrong,
    muted = LightMuted,
    inkButton = LightInkButton,
    onInkButton = LightOnInkButton,
    success = LightSuccess,
)

val DarkOotColors = OotExtendedColors(
    hairline = DarkHairline,
    hairlineStrong = DarkHairlineStrong,
    muted = DarkMuted,
    inkButton = DarkInkButton,
    onInkButton = DarkOnInkButton,
    success = DarkSuccess,
)

val LocalOotColors = staticCompositionLocalOf { LightOotColors }

val LightColorScheme = lightColorScheme(
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceTint = LightSurfaceTint,
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = LightScrim,
)

val DarkColorScheme = darkColorScheme(
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceTint = DarkSurfaceTint,
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim,
)
