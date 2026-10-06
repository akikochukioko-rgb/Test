package eu.akaiko.rplayer

import android.content.Context

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromKey(key: String?): AppThemeMode = when (key) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }

    val key: String
        get() = when (this) {
            SYSTEM -> "system"
            LIGHT -> "light"
            DARK -> "dark"
        }

    val labelRu: String
        get() = when (this) {
            SYSTEM -> "Как в системе"
            LIGHT -> "Светлая"
            DARK -> "Тёмная"
        }
}

object ThemePrefs {
    private const val PREFS = "appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_CHOSEN = "theme_chosen"

    fun isChosen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHOSEN, false)

    fun load(context: Context): AppThemeMode {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppThemeMode.fromKey(p.getString(KEY_MODE, "system"))
    }

    fun save(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.key)
            .putBoolean(KEY_CHOSEN, true)
            .apply()
    }
}
