package com.loadalert.ui.settings

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * Doze and vendor battery managers can defer a WorkManager job well past its deadline. For most
 * apps that is fine; for one whose entire job is a timely reminder it is the difference between
 * working and not, so we surface it rather than letting it fail quietly.
 */
object BatteryOptimization {

    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system list of battery-optimised apps.
     *
     * Deliberately not the one-tap REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog: that needs a
     * permission Google restricts on Play, and this screen works everywhere without it.
     */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            // Some vendor builds hide that screen; the app's own settings page is the fallback.
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(target)
    }
}
