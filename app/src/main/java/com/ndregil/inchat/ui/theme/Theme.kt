package com.ndregil.inchat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import android.app.Activity
import com.ndregil.inchat.data.model.ThemePreferences

val LocalThemePreferences = compositionLocalOf { ThemePreferences() }

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = Color(0xFF00354E),
    primaryContainer = Color(0xFF004D70),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = TealAccent,
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFF70F8E4),
    tertiary = IndigoSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = CardBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEFAF2),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = IndigoSecondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = CardBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customAccentColor: Color? = null,
    themePreferences: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit,
) {
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (customAccentColor != null) {
        colorScheme = colorScheme.copy(
            primary = customAccentColor,
            primaryContainer = customAccentColor.copy(alpha = 0.2f),
            outline = customAccentColor.copy(alpha = 0.5f)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val typography = remember(themePreferences.appFontSizeSp, themePreferences.fontFamily) {
        createScaledTypography(
            appFontSizeSp = themePreferences.appFontSizeSp,
            fontFamilyKey = themePreferences.fontFamily
        )
    }

    CompositionLocalProvider(LocalThemePreferences provides themePreferences) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
