package com.king.mob_app_assignment.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the email/password needed to silently re-run a normal Firebase email/password sign-in
 * after a successful [BiometricPrompt] authentication - "sign in with fingerprint/face" instead
 * of retyping credentials, per the user's explicit choice of this pattern over any alternative.
 *
 * Deliberately NOT just [SecurePrefsManager]'s EncryptedSharedPreferences: that scheme protects
 * data at rest (Keystore-backed encryption) but nothing stops the app from decrypting it the
 * instant it's read - there's no biometric gate on the decryption itself. Here the AES key used
 * to encrypt the stored password is generated with
 * [KeyGenParameterSpec.Builder.setUserAuthenticationRequired] = true, which means the Android
 * Keystore itself refuses to unlock that key for use until a fresh biometric authentication has
 * succeeded - the gate is enforced by the OS/hardware, not just app logic that could be bypassed
 * by anyone who can read the app's private storage.
 *
 * Threat model note (see PLACEHOLDERS_TODO.csv): the plaintext password still exists briefly in
 * process memory after decryption, and Firebase Auth itself receives it in plaintext over its
 * own (TLS-protected) sign-in call, same as a normal manual login - biometric-gated Keystore
 * encryption protects the credential at rest on the device, it does not change what Firebase
 * Auth's own sign-in API requires.
 */
@Singleton
class BiometricCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** True once the user has opted in and a credential is actually stored for this device. */
    fun isEnabled(): Boolean = prefs.contains(KEY_EMAIL) && prefs.contains(KEY_ENCRYPTED_PASSWORD)

    fun enabledEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /**
     * Returns a [BiometricPrompt.CryptoObject] wrapping a decrypt-mode [Cipher] initialized
     * against the biometric-gated key. Pass this to [BiometricPrompt.authenticate] - the Cipher
     * only becomes usable once BiometricPrompt reports success, which is what makes this
     * approach actually enforce the gate at the Keystore level rather than trusting the caller
     * to check a boolean after the fact.
     */
    fun buildDecryptCryptoObject(): BiometricPrompt.CryptoObject? {
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            BiometricPrompt.CryptoObject(cipher)
        }.getOrNull()
    }

    /** Call from the BiometricPrompt success callback with the now-authenticated Cipher. */
    fun decryptPassword(cipher: Cipher): String? {
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_PASSWORD, null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * Enables biometric login: generates (or reuses) the biometric-gated Keystore key, encrypts
     * [password], and stores the ciphertext + email. Encryption itself does NOT require a
     * biometric prompt (only decryption does, per [KeyGenParameterSpec] below) - this runs right
     * after the user's own successful manual password login, while the password is already
     * in hand, so there's nothing gained by prompting again just to enable the feature.
     */
    fun enable(email: String, password: String): Boolean = runCatching {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_ENCRYPTED_PASSWORD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        true
    }.getOrDefault(false)

    /** Disables biometric login and wipes the stored credential (does not touch the Keystore key). */
    fun disable() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // The actual biometric gate: the Keystore will not unlock this key for decrypt use
            // without a fresh, successful biometric authentication having just occurred.
            .setUserAuthenticationRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_FILE_NAME = "biometric_credential_prefs"
        const val KEY_EMAIL = "biometric_email"
        const val KEY_ENCRYPTED_PASSWORD = "biometric_encrypted_password"
        const val KEY_IV = "biometric_iv"
        const val KEYSTORE_ALIAS = "biometric_login_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
