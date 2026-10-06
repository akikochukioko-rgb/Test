package eu.akaiko.rplayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val photoPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requestAllFilesAccessSafely()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState == null) requestPermissionsAtStart()
        } catch (t: Throwable) {
            CrashLog.append("MAIN", "requestPermissionsAtStart failed", t)
        }
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this@MainActivity)) }
            // First launch forces settings; later opened via gear icon
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
                RadioApp(
                    onOpenSettings = { showSettings = true }
                )
                if (showSettings) {
                    SettingsDialog(
                        themeMode = themeMode,
                        requireThemeChoice = firstRun && !ThemePrefs.isChosen(this@MainActivity),
                        onThemeSelected = { mode ->
                            ThemePrefs.save(this@MainActivity, mode)
                            themeMode = mode
                        },
                        onDismiss = {
                            if (ThemePrefs.isChosen(this@MainActivity)) {
                                showSettings = false
                            }
                        }
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
        if (perms.isNotEmpty()) {
            photoPermLauncher.launch(perms.toTypedArray())
        } else {
            requestAllFilesAccessSafely()
        }
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

@Composable
private fun SettingsDialog(
    themeMode: AppThemeMode,
    requireThemeChoice: Boolean,
    onThemeSelected: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!requireThemeChoice) onDismiss() },
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Тема оформления",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Можно сменить в любой момент через иконку ⚙",
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
                            mode.labelRu,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !requireThemeChoice || ThemePrefs.isChosen(
                    androidx.compose.ui.platform.LocalContext.current
                ),
                onClick = onDismiss
            ) {
                Text(if (requireThemeChoice) "Готово" else "Закрыть")
            }
        }
    )
}
