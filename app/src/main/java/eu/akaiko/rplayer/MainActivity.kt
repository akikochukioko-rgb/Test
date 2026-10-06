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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

class MainActivity : ComponentActivity() {

    private val photoPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Defer heavy system screens; only ask all-files if still needed.
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
            val dark = isSystemInDarkTheme()
            // dynamic*ColorScheme requires API 31+; calling it on older MIUI (Android 10/11) crashes on launch.
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                }
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { RadioApp() }
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
