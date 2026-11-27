package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldSoftContainer,
    onPrimaryContainer = Color(0xFF3E2600),
    secondary = CopperAccent,
    onSecondary = Color.White,
    secondaryContainer = CopperSoft,
    onSecondaryContainer = Color(0xFF4A1B0A),
    tertiary = EmeraldStatus,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldSoft,
    onTertiaryContainer = Color(0xFF0A3322),
    background = IvoryBackground,
    onBackground = WarmTextPrimary,
    surface = IvoryCard,
    onSurface = WarmTextPrimary,
    surfaceVariant = IvoryMutedSurface,
    onSurfaceVariant = WarmTextSecondary,
    outline = IvoryOutline,
    error = CrimsonError,
    errorContainer = CrimsonSoft,
    onErrorContainer = Color(0xFF410002)
)

val RealizeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RealizeBarbershopTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = RealizeTypography,
        shapes = RealizeShapes,
        content = content
    )
}
