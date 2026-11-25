package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.ThemeMode

private val NoorLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = GoldBurnished,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF5A3D08),
    tertiary = EmeraldVibrant,
    onTertiary = Color.White,
    background = IvoryBackground,
    onBackground = InkCharcoal,
    surface = PureWhiteSurface,
    onSurface = InkCharcoal,
    surfaceVariant = ParchmentContainer,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = PureWhiteSurface,
    surfaceContainer = ParchmentContainer,
    surfaceContainerHigh = ParchmentContainerHigh,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
    error = ErrorLight
)

private val NoorDarkColorScheme = darkColorScheme(
    primary = EmeraldLuminous,
    onPrimary = Color(0xFF04291E),
    primaryContainer = EmeraldNightContainer,
    onPrimaryContainer = EmeraldLuminous,
    secondary = GoldChampagne,
    onSecondary = Color(0xFF2B2007),
    secondaryContainer = GoldNightContainer,
    onSecondaryContainer = GoldChampagne,
    tertiary = EmeraldLuminous,
    onTertiary = Color(0xFF04291E),
    background = ObsidianBackground,
    onBackground = IvoryMist,
    surface = ObsidianSurface,
    onSurface = IvoryMist,
    surfaceVariant = ObsidianContainer,
    onSurfaceVariant = JadeMuted,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianContainer,
    surfaceContainerHigh = ObsidianContainerHigh,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
    error = ErrorDark
)

val NoorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NoorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) NoorDarkColorScheme else NoorLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NoorTypography,
        shapes = NoorShapes,
        content = content
    )
}
