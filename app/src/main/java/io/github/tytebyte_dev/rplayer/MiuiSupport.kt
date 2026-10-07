package io.github.tytebyte_dev.rplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Xiaomi MIUI / HyperOS helpers: detect shell, open Autostart & battery screens,
 * request ignore-battery-optimizations so background radio is less likely to be killed.
 */
object MiuiSupport {
    private const val TAG = "MiuiSupport"

    /** True on Xiaomi / Redmi / POCO or when MIUI/HyperOS system props are present. */
    fun isMiuiOrHyperOs(): Boolean {
        if (brandLooksXiaomi()) return true
        val miui = systemProp("ro.miui.ui.version.name")
        val hyper = systemProp("ro.mi.os.version.name")
            ?: systemProp("ro.hyper.os.version.name")
        return !miui.isNullOrBlank() || !hyper.isNullOrBlank()
    }

    fun brandLooksXiaomi(): Boolean {
        val m = Build.MANUFACTURER.orEmpty()
        val b = Build.BRAND.orEmpty()
        return listOf(m, b).any {
            it.equals("xiaomi", true) ||
                it.equals("redmi", true) ||
                it.equals("poco", true) ||
                it.equals("blackshark", true)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** System dialog: allow unrestricted background (Android + works on MIUI). */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (isIgnoringBatteryOptimizations(context)) return false
        return tryStart(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }

    /** Open MIUI / HyperOS Autostart management (list of apps). */
    fun openAutostartSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.securitycenter.permission.autostart.AutoStartManagementActivity"
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )
        for (cn in candidates) {
            val intent = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(context, intent)) return true
        }
        return openAppDetails(context)
    }

    /** MIUI battery / power-keeper screens (try several known activities). */
    fun openBatterySaverSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            ),
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings"
            )
        )
        for (cn in candidates) {
            val intent = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
            }
            if (tryStart(context, intent)) return true
        }
        return tryStart(
            context,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        ) || openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStart(context, intent)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.d(TAG, "Intent failed: ${intent.component ?: intent.action}", e)
        false
    }

    private fun systemProp(key: String): String? = try {
        val clz = Class.forName("android.os.SystemProperties")
        val get = clz.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
