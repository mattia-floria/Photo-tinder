package com.floria.phototinder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
import androidx.core.view.WindowCompat

private val DarkColorSchemeDefault = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    onBackground = Color.White,
    onPrimaryContainer = Color.White,
    onSecondaryContainer = Color.White,
    onTertiaryContainer = Color.White
)

private val LightColorSchemeDefault = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    onSurface = Color.Black,
    onSurfaceVariant = Color.Black,
    onBackground = Color.Black,
    onPrimaryContainer = Color.Black,
    onSecondaryContainer = Color.Black,
    onTertiaryContainer = Color.Black
)

@Composable
fun PhotoTinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    tertiaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    // Explicitly check for our dynamic signal (null colors)
    val useDynamic = supportsDynamic && (primaryColor == null || primaryColor == Color.Transparent)

    val colorScheme: ColorScheme = when {
        useDynamic -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context).let { base ->
                    base.copy(
                        onSurface = Color.White,
                        onSurfaceVariant = Color.White,
                        onBackground = Color.White,
                        onPrimaryContainer = Color.White,
                        onSecondaryContainer = Color.White,
                        onTertiaryContainer = Color.White,
                        background = if (pureBlack) Color.Black else base.background,
                        surface = if (pureBlack) Color.Black else base.surface
                    )
                }
            } else {
                dynamicLightColorScheme(context).copy(
                    onSurface = Color.Black,
                    onSurfaceVariant = Color.Black,
                    onBackground = Color.Black,
                    onPrimaryContainer = Color.Black,
                    onSecondaryContainer = Color.Black,
                    onTertiaryContainer = Color.Black
                )
            }
        }
        primaryColor != null && primaryColor != Color.Transparent -> {
            // Use custom palette if primary is provided
            val p = primaryColor
            val s = secondaryColor ?: p // Fallback to primary if missing
            val t = tertiaryColor ?: s // Fallback to secondary if missing
            
            if (darkTheme) {
                darkColorScheme(
                    primary = p,
                    onPrimary = Color.Black,
                    primaryContainer = p.copy(alpha = 0.4f),
                    onPrimaryContainer = Color.White,
                    secondary = s,
                    onSecondary = Color.Black,
                    secondaryContainer = s.copy(alpha = 0.4f),
                    onSecondaryContainer = Color.White,
                    tertiary = t,
                    onTertiary = Color.Black,
                    tertiaryContainer = t.copy(alpha = 0.4f),
                    onTertiaryContainer = Color.White,
                    background = if (pureBlack) Color.Black else Color(0xFF121212),
                    surface = if (pureBlack) Color.Black else Color(0xFF121212),
                    onSurface = Color.White,
                    onBackground = Color.White,
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color.White,
                    outline = p.copy(alpha = 0.5f)
                )
            } else {
                lightColorScheme(
                    primary = p,
                    onPrimary = Color.White,
                    primaryContainer = p.copy(alpha = 0.2f),
                    onPrimaryContainer = Color.Black,
                    secondary = s,
                    onSecondary = Color.White,
                    secondaryContainer = s.copy(alpha = 0.2f),
                    onSecondaryContainer = Color.Black,
                    tertiary = t,
                    onTertiary = Color.White,
                    tertiaryContainer = t.copy(alpha = 0.2f),
                    onTertiaryContainer = Color.Black,
                    background = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black,
                    onBackground = Color.Black,
                    surfaceVariant = Color(0xFFF0F0F0),
                    onSurfaceVariant = Color.Black,
                    outline = p.copy(alpha = 0.5f)
                )
            }
        }
        darkTheme -> {
            if (pureBlack) {
                DarkColorSchemeDefault.copy(background = Color.Black, surface = Color.Black)
            } else {
                DarkColorSchemeDefault
            }
        }
        else -> LightColorSchemeDefault
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
