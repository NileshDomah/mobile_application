package com.king.mob_app_assignment.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Firebase Auth persists its own ID/refresh tokens internally (Keystore-backed on the platform
// side); we intentionally never touch or duplicate that here. This class only stores small,
// non-sensitive UX preferences (remember-me flag, last used email) but still uses
// EncryptedSharedPreferences since even an email address is user PII worth protecting at rest.
@Singleton
class SecurePrefsManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var isRememberMeEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply()

    var lastUsedEmail: String?
        get() = prefs.getString(KEY_LAST_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_LAST_EMAIL, value).apply()

    fun clear() {
        prefs.edit().remove(KEY_LAST_EMAIL).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "secure_user_prefs"
        const val KEY_REMEMBER_ME = "remember_me"
        const val KEY_LAST_EMAIL = "last_used_email"
    }
}
