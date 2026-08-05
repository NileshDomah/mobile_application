package com.king.mob_app_assignment.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin wrapper around FirebaseCrashlytics so call sites (ViewModels/Repositories) don't need
 * a direct Firebase import and so user context is set consistently in exactly one place.
 * Never pass email/displayName here - only the Firebase uid, to avoid logging PII.
 */
object CrashReporter {

    fun initialize() {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }

    fun setUserId(uid: String?) {
        FirebaseCrashlytics.getInstance().setUserId(uid.orEmpty())
    }

    fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }

    fun recordException(throwable: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}
