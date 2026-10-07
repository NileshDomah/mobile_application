package com.king.mob_app_assignment.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_settings")

private val HAS_COMPLETED_APP_LOCK_SETUP = booleanPreferencesKey("has_completed_app_lock_setup")

/**
 * Tracks whether this device has completed app-lock setup (a PIN, required, optionally paired
 * with biometric - see AppPinStore/BiometricCredentialStore) at all - drives whether RootNavGraph
 * shows SetupAppLockScreen (first time, right after a successful email/password login) or
 * AppLockScreen (every later app entry while a Firebase session persists) in place of the old
 * "sign in with email/password every time" flow.
 *
 * Deliberately a separate persisted flag from "is the user currently unlocked this session" -
 * that part lives only in-memory (see MainActivity's own isAppUnlockedThisSession state), since
 * being unlocked should never survive the process being killed and restarted; whether SETUP has
 * ever been completed absolutely should survive that, or the user would be asked to create a new
 * PIN every time they reopen the app.
 */
@Singleton
class AppLockPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasCompletedSetupFlow: Flow<Boolean> = context.appLockDataStore.data.map { prefs ->
        prefs[HAS_COMPLETED_APP_LOCK_SETUP] ?: false
    }

    suspend fun setSetupCompleted() {
        context.appLockDataStore.edit { prefs -> prefs[HAS_COMPLETED_APP_LOCK_SETUP] = true }
    }

    /** Called on sign-out - a new sign-in (same or different account) goes through setup again. */
    suspend fun resetSetupCompleted() {
        context.appLockDataStore.edit { prefs -> prefs[HAS_COMPLETED_APP_LOCK_SETUP] = false }
    }
}
