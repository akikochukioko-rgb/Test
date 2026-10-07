package io.github.tytebyte_dev.rplayer

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build

enum class AppTheme { SYSTEM, LIGHT, DARK }

object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY = "theme"
    private const val FIRST = "theme_chosen"

    fun get(ctx: Context): AppTheme {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return runCatching { AppTheme.valueOf(name) }.getOrDefault(AppTheme.SYSTEM)
    }

    fun set(ctx: Context, theme: AppTheme) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, theme.name)
            .putBoolean(FIRST, true)
            .apply()
    }

    fun isChosen(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(FIRST, false)
}

@Composable
fun resolveColorScheme(theme: AppTheme): ColorScheme {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }
    val ctx = LocalContext.current
    return if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
}
