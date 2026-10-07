package com.king.mob_app_assignment.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the device PIN as a salted SHA-256 hash, never the plaintext digits - unlike
 * [BiometricCredentialStore], which deliberately stores a REVERSIBLE (encrypted) password because
 * it needs the real password back to silently re-run a Firebase sign-in. A PIN only ever needs to
 * be VERIFIED (does what the user just typed match what they set?), never recovered, so hashing
 * is the correct (one-way, no way to leak the real PIN even if this file's encryption were ever
 * broken) primitive here - the same reason real password storage never keeps a reversible copy.
 *
 * Backed by EncryptedSharedPreferences (Keystore-derived master key) same as [SecurePrefsManager]
 * - this protects the stored hash+salt at rest, on top of the hash itself already being one-way.
 *
 * The PIN is the universal, required fallback unlock method (see AppLockPreferences) - biometric
 * is an additional, optional convenience layered on top, never a replacement for it, so a user is
 * never stuck locked out just because biometric enrollment changed or hardware became unavailable.
 */
@Singleton
class AppPinStore @Inject constructor(
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

    /** True once a PIN has actually been set for this device. */
    fun isPinSet(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    /** Hashes and stores [pin] as this device's unlock PIN, replacing any previous one. */
    fun setPin(pin: String) {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .apply()
    }

    /** True if [pin] matches the currently stored PIN - false (never throws) if no PIN is set yet. */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
        val salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)
        return hashPin(pin, salt) == storedHash
    }

    /** Removes the stored PIN entirely - used when the user signs out or disables app-lock. */
    fun clearPin() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(hashed, android.util.Base64.NO_WRAP)
    }

    private companion object {
        const val PREFS_FILE_NAME = "app_pin_prefs"
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val SALT_LENGTH_BYTES = 16
    }
}
