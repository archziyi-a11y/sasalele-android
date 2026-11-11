package com.example.sasalele.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    background = Color(0xFF1C1B1A),
    surface = Color(0xFF242220),
    surfaceVariant = Color(0xFF494440),
    onSurface = Color(0xFFECE0DA),
    onSurfaceVariant = Color(0xFFD0C4BD),
    outline = Color(0xFF9B8E87),
    primaryContainer = Color(0xFF71312D),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFD7C2B8),
    onSecondary = Color(0xFF3C302A),
    secondaryContainer = Color(0xFF534039),
    onSecondaryContainer = Color(0xFFF3DED4),
    tertiary = Color(0xFFD8C6A4),
    onTertiary = Color(0xFF3A301E),
    tertiaryContainer = Color(0xFF524630),
    onTertiaryContainer = Color(0xFFF4E1BF),
    outlineVariant = Color(0xFF50443E),
    surfaceContainerLowest = Color(0xFF171513),
    surfaceContainerLow = Color(0xFF201D1B),
    surfaceContainer = Color(0xFF292522),
    surfaceContainerHigh = Color(0xFF342F2C),
    surfaceContainerHighest = Color(0xFF3F3935)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB53D38),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    surfaceVariant = Color(0xFFEDE1DA),
    onSurface = Color(0xFF251F1C),
    onSurfaceVariant = Color(0xFF50443E),
    outline = Color(0xFF83736B),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410004),
    secondary = Color(0xFF76584E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3DED4),
    onSecondaryContainer = Color(0xFF2D1B15),
    tertiary = Color(0xFF705C3E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE0B8),
    onTertiaryContainer = Color(0xFF281A05),
    outlineVariant = Color(0xFFD5C3B9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFF8EDE6),
    surfaceContainerHigh = Color(0xFFF2E7E0),
    surfaceContainerHighest = Color(0xFFECE1DA)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SASALELETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colorScheme.onSurface,
                content = content
            )
        }
    )
}
