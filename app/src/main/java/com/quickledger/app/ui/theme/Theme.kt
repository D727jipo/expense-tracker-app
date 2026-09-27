package com.quickledger.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.quickledger.app.ThemePrefs

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FD),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFFB0590F),
    surface = Color(0xFFF4FBFB),
    background = Color(0xFFF4FBFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CD9E1),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF6FF6FD),
    secondary = Color(0xFFB1CBCE),
    onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF334B4E),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFFFB77C),
    surface = Color(0xFF101D1E),
    background = Color(0xFF0D1516),
)

@Composable
fun LedgerTheme(
    themeMode: Int,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemePrefs.MODE_LIGHT -> false
        ThemePrefs.MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
