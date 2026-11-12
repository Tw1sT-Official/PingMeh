package com.tw1st.pingmeh.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFF0000), // Red
    secondary = androidx.compose.ui.graphics.Color(0xFFAA0000), // Darker Red
    background = androidx.compose.ui.graphics.Color(0xFF000000), // Black
    surface = androidx.compose.ui.graphics.Color(0xFF000000), // Black
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White,
    outline = androidx.compose.ui.graphics.Color(0xFFFF0000), // Red borders
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1E1E1E), // Slightly lighter black for cards/dialogs
    onSurfaceVariant = androidx.compose.ui.graphics.Color.White
)

@Composable
fun PingMehTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}