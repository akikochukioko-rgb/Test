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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            var showThemePicker by remember {
                mutableStateOf(!ThemePrefs.isChosen(this@MainActivity))
            }

            val systemDark = isSystemInDarkTheme()
            val useDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }

            // dynamic*ColorScheme only on API 31+ (safe on older MIUI)
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
                    onOpenThemePicker = { showThemePicker = true }
                )
                if (showThemePicker) {
                    ThemePickerDialog(
                        current = themeMode,
                        onPick = { mode ->
                            ThemePrefs.save(this@MainActivity, mode)
                            themeMode = mode
                            showThemePicker = false
                        },
                        // First launch: must choose; later opens from menu can dismiss
                        dismissible = ThemePrefs.isChosen(this@MainActivity)
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
private fun ThemePickerDialog(
    current: AppThemeMode,
    onPick: (AppThemeMode) -> Unit,
    dismissible: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (dismissible) onPick(current) },
        title = { Text("Тема оформления") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Выберите внешний вид приложения:")
                AppThemeMode.entries.forEach { mode ->
                    TextButton(
                        onClick = { onPick(mode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (mode == current) "●  ${mode.labelRu}" else "○  ${mode.labelRu}"
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = if (dismissible) {
            {
                TextButton(onClick = { onPick(current) }) { Text("Закрыть") }
            }
        } else null
    )
}
