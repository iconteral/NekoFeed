package com.ico.nekofeed.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

object ExpressiveTokens {
    val SplitButtonOuterCorner = 20.dp
    val SplitButtonInnerCorner = 0.dp
    val WaveAmplitude = 1f
    val WaveSpeed = 1f
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = PrimaryDark,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimaryLight,
    secondary = SecondaryLight,
    onSecondary = SecondaryDark,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SecondaryLight,
    tertiary = TertiaryLight,
    onTertiary = TertiaryDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Outline,
    error = AccentRed,
    onError = DarkOnBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = LightSurface,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = PrimaryDark,
    secondary = Secondary,
    onSecondary = LightSurface,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = SecondaryDark,
    tertiary = Tertiary,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Outline,
    error = AccentRed,
    onError = LightSurface,
)

@Composable
fun NekoFeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 禁用动态颜色以保持品牌一致性
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NekoFeedTypography,
        content = content
    )
}
