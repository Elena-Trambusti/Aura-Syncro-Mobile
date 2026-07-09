package com.aurasyncromobile.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BrandDark,
    surface = BrandDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = BrandDark,
    surface = BrandDark,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun AuraSyncroMobileTheme(
    darkTheme: Boolean = true, // Forziamo il tema scuro per l'effetto Luxury
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabilitiamo i colori dinamici per mantenere l'identità del brand
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    // Rimosso il dynamic color che sovrascriverebbe i colori del brand

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            val view = LocalContext.current as? Activity
            view?.window?.let { window ->
                window.statusBarColor = BrandDark.toArgb()
                window.navigationBarColor = BrandDark.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            content()
        }
    )
}