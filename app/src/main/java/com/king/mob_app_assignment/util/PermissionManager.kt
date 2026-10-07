package com.king.mob_app_assignment.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class AppPermission(val manifestPermission: String, val label: String) {
    CAMERA(Manifest.permission.CAMERA, "Camera"),
    LOCATION(Manifest.permission.ACCESS_FINE_LOCATION, "Location"),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            "android.permission.POST_NOTIFICATIONS"
        },
        "Notifications",
    ),
}

// Motion/accelerometer + magnetometer sensors used for shake detection and compass heading do
// not require a runtime permission grant on API 28+ (they are normal, not dangerous,
// protection-level sensors), so there is no BODY_SENSORS-style request needed for them here.
object PermissionManager {

    fun isGranted(context: Context, permission: AppPermission): Boolean {
        if (permission == AppPermission.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            permission.manifestPermission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // See PermissionRequestHistory's own doc comment for the exact bug this fixes ("the allow
    // option should pop up in the app instead of redirecting to phone settings") -
    // shouldShowRequestPermissionRationale() alone can't tell "never asked yet" apart from
    // "genuinely denied with don't-ask-again", both report false. Only treating that false as
    // permanently denied once PermissionRequestHistory confirms a real request already happened
    // means a brand-new install always gets the real system dialog first, not a Settings redirect.
    fun isPermanentlyDenied(activity: Activity, permission: AppPermission): Boolean {
        if (permission == AppPermission.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        val granted = isGranted(activity, permission)
        if (granted) return false
        if (!PermissionRequestHistory.hasRequestedBefore(activity, permission)) return false
        return !activity.shouldShowRequestPermissionRationale(permission.manifestPermission)
    }

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
