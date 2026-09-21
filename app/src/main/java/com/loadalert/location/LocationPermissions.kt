package com.loadalert.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Central place for the permission questions the rest of the app asks. */
object LocationPermissions {

    const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
    const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
    const val BACKGROUND = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    val FOREGROUND_PERMISSIONS = arrayOf(FINE, COARSE)

    fun hasForegroundLocation(context: Context): Boolean =
        isGranted(context, FINE) || isGranted(context, COARSE)

    fun hasPreciseLocation(context: Context): Boolean = isGranted(context, FINE)

    /**
     * Background location only exists as a separate grant on Android 10+. Below that, holding
     * foreground location is enough for a worker to read position while the app is not visible.
     */
    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isGranted(context, BACKGROUND)
        } else {
            hasForegroundLocation(context)
        }

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
