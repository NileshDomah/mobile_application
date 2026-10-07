package com.king.mob_app_assignment.util

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks whether the system permission dialog has EVER actually been shown for a given
 * [AppPermission] on this device - a plain SharedPreferences flag, set the moment a request is
 * launched (see StartupPermissionGate's own use of it).
 *
 * ROOT CAUSE this exists to fix (user-reported: "for permissions the allow option should pop up
 * in the app instead of redirecting the user to the phone settings") -
 * Activity.shouldShowRequestPermissionRationale() returns false in TWO different situations
 * Android itself does not distinguish between:
 *   1. The permission was denied once already with "Don't ask again" checked - genuinely
 *      permanently denied, Settings really is the only way back in.
 *   2. The permission has NEVER been requested at all yet - the system has no rationale-hint
 *      state to report either way, so it also reports false here, on the very first ask.
 * PermissionManager.isPermanentlyDenied() used to treat both as the same "permanently denied"
 * case, which sent a user straight to Settings on their very first launch, before Android's own
 * real permission dialog had ever even had a chance to show - exactly what was reported. This
 * store lets isPermanentlyDenied() tell the two apart: only treat shouldShowRequestPermissionRationale
 * == false as permanently denied once we know for certain a real request has already happened at
 * least once before.
 */
object PermissionRequestHistory {
    private const val PREFS_FILE_NAME = "permission_request_history"

    fun hasRequestedBefore(context: Context, permission: AppPermission): Boolean {
        val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(permission.name, false)
    }

    fun markRequested(context: Context, permission: AppPermission) {
        val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(permission.name, true) }
    }
}
