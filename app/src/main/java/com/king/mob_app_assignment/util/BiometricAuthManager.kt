package com.king.mob_app_assignment.util

import android.content.Context
import androidx.biometric.BiometricManager

/**
 * Capability check for BiometricPrompt - mirrors PermissionManager's shape (a small
 * status-check object) but checks hardware/enrollment via BiometricManager.canAuthenticate()
 * rather than a runtime Manifest permission grant, since USE_BIOMETRIC is a normal
 * (auto-granted) permission with no request flow of its own.
 */
object BiometricAuthManager {

    const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** True only when the device has usable biometric hardware AND the user has enrolled at least one. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Human-readable reason biometric login isn't offered, for a settings-screen hint - null when available. */
    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no biometric hardware."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or face is enrolled on this device yet."
            else -> "Biometric login isn't available on this device."
        }
}
