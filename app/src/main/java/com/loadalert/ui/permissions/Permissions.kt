package com.loadalert.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.loadalert.location.LocationPermissions

/** The three grants LoadAlert needs, in the order Android insists they be asked for. */
enum class AppPermission {
    NOTIFICATIONS,
    LOCATION,
    BACKGROUND_LOCATION,
}

data class PermissionCopy(val title: String, val rationale: String, val cta: String)

fun copyFor(permission: AppPermission): PermissionCopy = when (permission) {
    AppPermission.NOTIFICATIONS -> PermissionCopy(
        title = "Allow notifications",
        rationale = "Reminders are the whole point of LoadAlert. Without notification access " +
            "there is no way for the app to tell you your wash has finished.",
        cta = "Allow notifications",
    )

    AppPermission.LOCATION -> PermissionCopy(
        title = "Allow location access",
        rationale = "LoadAlert compares where you are with where the laundry room is, so it " +
            "only nags you when you are somewhere else. Your location never leaves your phone.",
        cta = "Allow location",
    )

    AppPermission.BACKGROUND_LOCATION -> PermissionCopy(
        title = "Allow location all the time",
        rationale = "The pick-up check runs while LoadAlert is closed. Android calls this " +
            "\"Allow all the time\". Without it the app cannot tell whether you have already " +
            "collected your laundry, so it will keep reminding you.",
        cta = "Open settings",
    )
}

fun isGranted(context: Context, permission: AppPermission): Boolean = when (permission) {
    AppPermission.NOTIFICATIONS -> LocationPermissions.hasNotificationPermission(context)
    AppPermission.LOCATION -> LocationPermissions.hasForegroundLocation(context)
    AppPermission.BACKGROUND_LOCATION -> LocationPermissions.hasBackgroundLocation(context)
}

/** Permissions that are still missing, ordered so the user is asked for them one at a time. */
fun missingPermissions(context: Context): List<AppPermission> =
    AppPermission.entries.filterNot { isGranted(context, it) }

/**
 * A card explaining one missing permission and requesting it.
 *
 * Background location is deliberately different: from Android 11 the system refuses to show a
 * dialog for it at all, so the only honest option is to walk the user to app settings.
 */
@Composable
fun PermissionRequestCard(
    permission: AppPermission,
    modifier: Modifier = Modifier,
    onResult: () -> Unit = {},
) {
    val context = LocalContext.current
    val copy = remember(permission) { copyFor(permission) }
    var deniedPermanently by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.any { it }
        if (!granted) {
            deniedPermanently = !shouldShowRationale(context, permission)
        }
        onResult()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(copy.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(copy.rationale, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        val needsSettings = deniedPermanently ||
                            (permission == AppPermission.BACKGROUND_LOCATION && requiresSettingsForBackground())
                        if (needsSettings) {
                            context.openAppSettings()
                        } else {
                            launcher.launch(permissionStrings(permission))
                        }
                    },
                ) {
                    Text(if (deniedPermanently) "Open settings" else copy.cta)
                }
                if (deniedPermanently) {
                    TextButton(onClick = onResult) { Text("I have granted it") }
                }
            }
        }
    }
}

/**
 * Android 11 removed the runtime dialog for background location; the grant can only be made
 * from the app's settings page. On Android 10 a normal request still works.
 */
private fun requiresSettingsForBackground(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

private fun permissionStrings(permission: AppPermission): Array<String> = when (permission) {
    AppPermission.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    AppPermission.LOCATION -> LocationPermissions.FOREGROUND_PERMISSIONS

    AppPermission.BACKGROUND_LOCATION ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(LocationPermissions.BACKGROUND)
        } else {
            emptyArray()
        }
}

private fun shouldShowRationale(context: Context, permission: AppPermission): Boolean {
    val activity = context as? Activity ?: return true
    return permissionStrings(permission).any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
