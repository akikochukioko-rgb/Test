package io.github.tytebyte_dev.rplayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val photoPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requestAllFilesAccessSafely()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.lang = LangPrefs.load(this)
        try {
            if (savedInstanceState == null) requestPermissionsAtStart()
        } catch (t: Throwable) {
            CrashLog.append("MAIN", "requestPermissionsAtStart failed", t)
        }
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this@MainActivity)) }
            var lang by remember { mutableStateOf(LangPrefs.load(this@MainActivity)) }
            var langTick by remember { mutableIntStateOf(0) }
            var showSettings by remember {
                mutableStateOf(!ThemePrefs.isChosen(this@MainActivity))
            }
            val firstRun = remember { !ThemePrefs.isChosen(this@MainActivity) }

            val systemDark = isSystemInDarkTheme()
            val useDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (useDark) dynamicDarkColorScheme(this@MainActivity)
                    else dynamicLightColorScheme(this@MainActivity)
                }
                useDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = scheme) {
                if (showSettings) {
                    SettingsScreen(
                        themeMode = themeMode,
                        lang = lang,
                        requireThemeChoice = firstRun && !ThemePrefs.isChosen(this@MainActivity),
                        onThemeSelected = { mode ->
                            ThemePrefs.save(this@MainActivity, mode)
                            themeMode = mode
                        },
                        onLangSelected = { l ->
                            LangPrefs.save(this@MainActivity, l)
                            AppStrings.lang = l
                            lang = l
                            langTick++
                        },
                        onBack = {
                            if (ThemePrefs.isChosen(this@MainActivity)) {
                                showSettings = false
                            }
                        }
                    )
                } else {
                    RadioApp(
                        onOpenSettings = { showSettings = true },
                        langTick = langTick
                    )
                }
            }
        }
    }

    private fun requestPermissionsAtStart() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (perms.isNotEmpty()) photoPermLauncher.launch(perms.toTypedArray())
        else requestAllFilesAccessSafely()
    }

    private fun requestAllFilesAccessSafely() {
        try {
            if (Environment.isExternalStorageManager()) return
            val uri = Uri.parse("package:$packageName")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e: Exception) {
                    CrashLog.append("MAIN", "Cannot open all-files settings", e)
                }
            }
        } catch (t: Throwable) {
            CrashLog.append("MAIN", "requestAllFilesAccess failed", t)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    themeMode: AppThemeMode,
    lang: AppLang,
    requireThemeChoice: Boolean,
    onThemeSelected: (AppThemeMode) -> Unit,
    onLangSelected: (AppLang) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val canLeave = !requireThemeChoice || ThemePrefs.isChosen(ctx)

    // System back: only leave when theme is chosen (first-run gate)
    BackHandler(enabled = canLeave) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.t("settings")) },
                navigationIcon = {
                    if (canLeave) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = AppStrings.t("back")
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                AppStrings.t("theme_section"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                AppStrings.t("theme_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            AppThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = themeMode == mode,
                            onClick = { onThemeSelected(mode) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeSelected(mode) }
                    )
                    Text(
                        mode.label(),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                AppStrings.t("lang_section"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            AppLang.entries.forEach { l ->
                val label = if (l == AppLang.RU) AppStrings.t("lang_ru") else AppStrings.t("lang_en")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = lang == l,
                            onClick = { onLangSelected(l) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = lang == l,
                        onClick = { onLangSelected(l) }
                    )
                    Text(
                        label,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
