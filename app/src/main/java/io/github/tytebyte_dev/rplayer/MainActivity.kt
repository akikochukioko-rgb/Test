package io.github.tytebyte_dev.rplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    /** Only POST_NOTIFICATIONS on API 33+ (media playback notification). */
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.lang = LangPrefs.load(this)
        try {
            if (savedInstanceState == null) requestNotificationPermissionIfNeeded()
        } catch (t: Throwable) {
            CrashLog.append("MAIN", "requestNotificationPermissionIfNeeded failed", t)
        }
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this@MainActivity)) }
            var lang by remember { mutableStateOf(LangPrefs.load(this@MainActivity)) }
            var langTick by remember { mutableIntStateOf(0) }
            // Settings only when user opens ⚙ — never forced at startup
            var showSettings by remember { mutableStateOf(false) }
            // One-time theme onboarding (full page, not settings)
            var showOnboarding by remember {
                mutableStateOf(!ThemePrefs.isChosen(this@MainActivity))
            }

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
                if (showOnboarding) {
                    ThemeOnboardingScreen(
                        themeMode = themeMode,
                        onThemeSelected = { mode ->
                            themeMode = mode
                        },
                        onContinue = {
                            ThemePrefs.save(this@MainActivity, themeMode)
                            showOnboarding = false
                        }
                    )
                } else {
                    // Keep RadioApp always composed so MediaController / playback survive Settings.
                    Box(Modifier.fillMaxSize()) {
                        RadioApp(
                            onOpenSettings = { showSettings = true },
                            langTick = langTick
                        )
                        if (showSettings) {
                            SettingsScreen(
                                themeMode = themeMode,
                                lang = lang,
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
                                onBack = { showSettings = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** One-time full-screen theme picker shown only on first launch. */
@Composable
private fun ThemeOnboardingScreen(
    themeMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    onContinue: () -> Unit
) {
    // Block system back — user must pick a theme and press Continue once
    BackHandler(enabled = true) { /* no-op */ }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                AppStrings.t("onboarding_title"),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                AppStrings.t("onboarding_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            AppThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = themeMode == mode,
                            onClick = { onThemeSelected(mode) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 10.dp),
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

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppStrings.t("onboarding_continue"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    themeMode: AppThemeMode,
    lang: AppLang,
    onThemeSelected: (AppThemeMode) -> Unit,
    onLangSelected: (AppLang) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(AppStrings.t("settings")) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = AppStrings.t("back")
                            )
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
}
