package io.github.tytebyte_dev.rplayer

import android.content.Context

enum class AppLang {
    RU, EN;

    companion object {
        fun fromKey(key: String?): AppLang = when (key) {
            "en" -> EN
            else -> RU
        }
    }

    val key: String get() = if (this == EN) "en" else "ru"
}

object LangPrefs {
    private const val PREFS = "appearance"
    private const val KEY = "lang"

    fun load(context: Context): AppLang {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppLang.fromKey(p.getString(KEY, "ru"))
    }

    fun save(context: Context, lang: AppLang) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, lang.key)
            .apply()
    }
}

/** In-app bilingual strings (RU default / EN). */
object AppStrings {
    @Volatile
    var lang: AppLang = AppLang.RU

    fun t(key: String): String {
        val map = if (lang == AppLang.EN) en else ru
        return map[key] ?: en[key] ?: key
    }

    private val ru = mapOf(
        "app_title" to "Радио",
        "settings" to "Настройки",
        "back" to "Назад",
        "onboarding_title" to "Выберите тему",
        "onboarding_subtitle" to "Оформление можно сменить позже в настройках ⚙",
        "onboarding_continue" to "Продолжить",
        "theme_section" to "Тема оформления",
        "theme_hint" to "Можно сменить в любой момент через иконку ⚙",
        "theme_system" to "Как в системе",
        "theme_light" to "Светлая",
        "theme_dark" to "Тёмная",
        "lang_section" to "Язык / Language",
        "lang_ru" to "Русский",
        "lang_en" to "English",
        "done" to "Готово",
        "close" to "Закрыть",
        "menu" to "Меню",
        "import_json" to "Импорт JSON",
        "export_json" to "Экспорт JSON",
        "miui_autostart" to "MIUI: автозапуск",
        "miui_battery" to "MIUI: батарея / фон",
        "miui_ignore_battery" to "Игнор оптимизации батареи",
        "miui_autostart_hint" to "Включите автозапуск для Radio Player",
        "miui_autostart_fail" to "Не удалось открыть настройки автозапуска",
        "miui_battery_hint" to "Выберите «Без ограничений» для приложения",
        "miui_battery_fail" to "Не удалось открыть настройки батареи",
        "battery_already" to "Оптимизация батареи уже отключена",
        "battery_allow" to "Разрешите работу в фоне",
        "battery_fail" to "Не удалось открыть диалог",
        "add_station" to "Добавить станцию",
        "edit" to "Изменить",
        "delete" to "Удалить",
        "play" to "Играть",
        "pause" to "Пауза",
        "empty_stations" to "Станций пока нет.\nНажмите «+» или импортируйте JSON.",
        "import_fail" to "Не удалось импортировать станции (пустой или неверный JSON)",
        "import_ok" to "Импортировано новых: %d (всего: %d)",
        "import_dup" to "Все станции из файла уже есть в списке",
        "export_ok" to "Экспортировано станций: %d",
        "export_fail" to "Ошибка записи файла экспорта",
        "export_empty" to "Нечего экспортировать — список пуст",
        "buffering" to "Буферизация…",
        "playing" to "Играет",
        "playing_prefix" to "Играет: %s",
        "playback_error" to "Ошибка воспроизведения",
        "new_station" to "Новая станция",
        "edit_station" to "Изменить станцию",
        "name" to "Название",
        "stream_url" to "Ссылка на поток (mp3)",
        "icon_url" to "Ссылка на иконку (необязательно)",
        "pick_file" to "Выбрать из файлов",
        "save" to "Сохранить",
        "cancel" to "Отмена",
    )

    private val en = mapOf(
        "app_title" to "Radio",
        "settings" to "Settings",
        "back" to "Back",
        "onboarding_title" to "Choose a theme",
        "onboarding_subtitle" to "You can change this later in Settings ⚙",
        "onboarding_continue" to "Continue",
        "theme_section" to "Theme",
        "theme_hint" to "You can change this anytime via the ⚙ icon",
        "theme_system" to "System default",
        "theme_light" to "Light",
        "theme_dark" to "Dark",
        "lang_section" to "Language",
        "lang_ru" to "Русский",
        "lang_en" to "English",
        "done" to "Done",
        "close" to "Close",
        "menu" to "Menu",
        "import_json" to "Import JSON",
        "export_json" to "Export JSON",
        "miui_autostart" to "MIUI: autostart",
        "miui_battery" to "MIUI: battery / background",
        "miui_ignore_battery" to "Ignore battery optimization",
        "miui_autostart_hint" to "Enable autostart for Radio Player",
        "miui_autostart_fail" to "Could not open autostart settings",
        "miui_battery_hint" to "Choose «No restrictions» for the app",
        "miui_battery_fail" to "Could not open battery settings",
        "battery_already" to "Battery optimization already disabled",
        "battery_allow" to "Allow background activity",
        "battery_fail" to "Could not open dialog",
        "add_station" to "Add station",
        "edit" to "Edit",
        "delete" to "Delete",
        "play" to "Play",
        "pause" to "Pause",
        "empty_stations" to "No stations yet.\nTap «+» or import JSON.",
        "import_fail" to "Could not import stations (empty or invalid JSON)",
        "import_ok" to "Imported new: %d (total: %d)",
        "import_dup" to "All stations from the file are already in the list",
        "export_ok" to "Exported stations: %d",
        "export_fail" to "Failed to write export file",
        "export_empty" to "Nothing to export — list is empty",
        "buffering" to "Buffering…",
        "playing" to "Playing",
        "playing_prefix" to "Playing: %s",
        "playback_error" to "Playback error",
        "new_station" to "New station",
        "edit_station" to "Edit station",
        "name" to "Name",
        "stream_url" to "Stream URL (mp3)",
        "icon_url" to "Icon URL (optional)",
        "pick_file" to "Pick from files",
        "save" to "Save",
        "cancel" to "Cancel",
    )
}
