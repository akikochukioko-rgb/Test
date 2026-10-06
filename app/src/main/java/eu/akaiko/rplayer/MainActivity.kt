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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

class MainActivity : ComponentActivity() {

    private val photoPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requestAllFilesAccess()
            requestBatteryAndMiui()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestPermissionsAtStart()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            ) { RadioApp() }
        }
    }

    /** Step 1: photo gallery (+ notifications). Step 2: all files + battery/MIUI. */
    private fun requestPermissionsAtStart() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        photoPermLauncher.launch(perms.toTypedArray())
    }

    private fun requestAllFilesAccess() {
        if (Environment.isExternalStorageManager()) return
        val uri = Uri.parse("package:$packageName")
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    /** Ignore battery optimizations; on MIUI also open Autostart once (prefs-gated). */
    private fun requestBatteryAndMiui() {
        MiuiSupport.requestIgnoreBatteryOptimizations(this)
        if (!MiuiSupport.isMiuiOrHyperOs()) return
        val prefs = getSharedPreferences("miui_setup", MODE_PRIVATE)
        if (prefs.getBoolean("autostart_prompted", false)) return
        prefs.edit().putBoolean("autostart_prompted", true).apply()
        // Open Autostart list so user can enable this app (cannot be set programmatically).
        MiuiSupport.openAutostartSettings(this)
    }
}
