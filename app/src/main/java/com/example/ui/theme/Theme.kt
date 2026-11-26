package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = Color(0xFFB3F7FF),
    secondary = WarmAmber,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Color(0xFFFFDFB0),
    tertiary = StudioViolet,
    onTertiary = Color.White,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = Color(0xFFE5DCFF),
    background = ObsidianBg,
    onBackground = Color(0xFFEAF0FA),
    surface = ConsoleSurface,
    onSurface = Color(0xFFEAF0FA),
    surfaceVariant = ConsoleSurfaceVariant,
    onSurfaceVariant = Color(0xFF9BA8C2),
    surfaceContainer = ConsoleSurfaceVariant,
    surfaceContainerHigh = ElevatedModule,
    outline = HairlineBorder,
    error = PeakRed,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = DaylightCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF4FA),
    onPrimaryContainer = Color(0xFF002A33),
    secondary = DaylightAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5BD),
    onSecondaryContainer = Color(0xFF331C00),
    tertiary = DaylightViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAE2FF),
    onTertiaryContainer = Color(0xFF1F0C54),
    background = DaylightBg,
    onBackground = DaylightOnBg,
    surface = DaylightSurface,
    onSurface = DaylightOnBg,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = Color(0xFF47546B),
    surfaceContainer = DaylightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDCE4F0),
    outline = Color(0xFFC2CEE0),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AudioStudioTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
