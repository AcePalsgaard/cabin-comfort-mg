package dk.Android.Cpmonster.mgmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dk.Android.Cpmonster.mgmanager.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = DPrimary, onPrimary = DOnPrimary,
    primaryContainer = DPrimaryContainer, onPrimaryContainer = DOnPrimaryContainer,
    secondary = DSecondary, onSecondary = DOnSecondary,
    secondaryContainer = DSecondaryContainer, onSecondaryContainer = DOnSecondaryContainer,
    tertiary = DTertiary,
    background = DSurface, onBackground = DOnSurface,
    surface = DSurface, onSurface = DOnSurface,
    surfaceVariant = DSurfaceVariant, onSurfaceVariant = DOnSurfaceVariant,
    outline = DOutline, outlineVariant = DOutlineVariant,
    surfaceDim = DSurface, surfaceBright = DContainerHighest,
    surfaceContainerLowest = DContainerLowest, surfaceContainerLow = DContainerLow,
    surfaceContainer = DContainer, surfaceContainerHigh = DContainerHigh,
    surfaceContainerHighest = DContainerHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = LPrimary, onPrimary = LOnPrimary,
    primaryContainer = LPrimaryContainer, onPrimaryContainer = LOnPrimaryContainer,
    secondary = LSecondary, onSecondary = LOnSecondary,
    secondaryContainer = LSecondaryContainer, onSecondaryContainer = LOnSecondaryContainer,
    tertiary = LTertiary,
    background = LSurface, onBackground = LOnSurface,
    surface = LSurface, onSurface = LOnSurface,
    surfaceVariant = LSurfaceVariant, onSurfaceVariant = LOnSurfaceVariant,
    outline = LOutline, outlineVariant = LOutlineVariant,
    surfaceDim = LContainerHighest, surfaceBright = LSurface,
    surfaceContainerLowest = LContainerLowest, surfaceContainerLow = LContainerLow,
    surfaceContainer = LContainer, surfaceContainerHigh = LContainerHigh,
    surfaceContainerHighest = LContainerHighest,
)

@Composable
fun MGManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    // Brand colours by default; wallpaper-based dynamic colour (Android 12+) is opt-in.
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

    // enableEdgeToEdge() picks status/nav bar icon colours from the *system* theme.
    // Follow the app's own theme instead, or a Dark app on a light phone gets dark-on-dark icons.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
