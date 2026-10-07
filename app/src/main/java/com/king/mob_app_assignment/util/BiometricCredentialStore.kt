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
     *
     * ROOT CAUSE (2026-09-10, user-reported "biometrics on existing account keep saying couldn't
     * be verified"): this used to swallow every failure via runCatching{}.getOrNull() with zero
     * logging - a caller getting null back (see AppLockScreen's own `?: return`) had no way to
     * tell "genuinely no credential stored yet" apart from "a real Keystore failure occurred."
     * The most likely real failure for an "existing account" (one that enabled biometric login
     * before app-lock existed) is KeyPermanentlyInvalidatedException: Android invalidates any
     * Keystore key generated with setUserAuthenticationRequired(true) the moment the device's
     * biometric enrollment changes AT ALL (a new fingerprint added, all fingerprints re-enrolled,
     * etc) - the key becomes permanently unusable, by design, since the biometric data it was
     * bound to no longer matches what's enrolled. That's not a bug in this app, it's the Keystore
     * doing exactly what it's supposed to - but the OLD code gave the user no way to recover
     * short of guessing to go disable-then-re-enable biometric login manually in Settings.
     */
    fun buildDecryptCryptoObject(): BiometricPrompt.CryptoObject? {
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            BiometricPrompt.CryptoObject(cipher)
        }.onFailure { throwable ->
            CrashReporter.log("BiometricCredentialStore.buildDecryptCryptoObject failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
            CrashReporter.recordException(throwable)
            if (throwable is android.security.keystore.KeyPermanentlyInvalidatedException) {
                // The stored credential can never successfully decrypt again (the Keystore key
                // is gone for good) - clearing it now means isEnabled()/isBiometricEnabled() flip
                // to false immediately, so the UI stops offering a biometric option that can only
                // ever fail, and the user falls through to PIN cleanly instead of hitting the
                // same dead end on every future attempt.
                disable()
            }
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
     * Builds an ENCRYPT-mode [BiometricPrompt.CryptoObject] to pass into
     * [BiometricPrompt.authenticate] when enabling biometric login. Every operation on this key
     * (encrypt AND decrypt - see [getOrCreateKey]'s setUserAuthenticationRequired(true), which
     * covers both purposes with no validity-duration grace period) requires its OWN fresh
     * Keystore-tracked authentication - it is NOT enough for a BiometricPrompt to merely report
     * onAuthenticationSucceeded in isolation; the Cipher that Keystore actually unlocks must be
     * the very one carried inside the CryptoObject that prompt authenticated against.
     *
     * ROOT CAUSE (2026-09-11, user-reported "keeps asking whether I want to enable it but never
     * does" - the SAME symptom as the 2026-09-10 fix noted on `enable()` below, which turned out
     * to be incomplete): SettingsScreen's BiometricPrompt.authenticate(promptInfo) call passed NO
     * CryptoObject at all, so a successful prompt there verified the user's biometric identity but
     * left the Keystore key's own per-operation auth-gate completely untouched. The subsequent
     * call into (the old) enable() then created its own brand-new Cipher and called
     * cipher.init(ENCRYPT_MODE, ...) on it - which immediately throws
     * UserNotAuthenticatedException again, since THIS Cipher was never the one Keystore just
     * authenticated, no matter how recently the (disconnected) prompt succeeded. That exception
     * was caught by enable()'s runCatching and turned into a silent `false`, reproducing exactly
     * the reported loop: prompt succeeds, dialog closes, biometric login is still off.
     */
    fun buildEnableCryptoObject(): BiometricPrompt.CryptoObject? = runCatching {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        BiometricPrompt.CryptoObject(cipher)
    }.onFailure { throwable ->
        CrashReporter.log("BiometricCredentialStore.buildEnableCryptoObject failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        CrashReporter.recordException(throwable)
    }.getOrNull()

    /**
     * Finishes enabling biometric login using the SAME [Cipher] instance BiometricPrompt just
     * authenticated (handed back inside its AuthenticationResult.cryptoObject) - see
     * [buildEnableCryptoObject]'s doc comment for why creating a fresh Cipher here instead would
     * fail every time. Encrypts [password] and stores the ciphertext + email.
     */
    fun enable(email: String, password: String, authenticatedCipher: Cipher): Boolean = runCatching {
        val encrypted = authenticatedCipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_ENCRYPTED_PASSWORD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(authenticatedCipher.iv, Base64.NO_WRAP))
            .apply()
        true
    }.onFailure { throwable ->
        // Was previously swallowed entirely (getOrDefault(false) with no logging) - "enable"
        // could fail for reasons that have nothing to do with a wrong password (no biometric
        // enrolled on this device, Keystore key generation rejecting
        // setUserAuthenticationRequired(true) on this API level/OEM, etc) and the caller had no
        // way to tell "enable failed" apart from "it worked" other than the boolean silently
        // being false - logging here is what made it possible to actually diagnose "enable
        // biometric doesn't work" instead of guessing.
        CrashReporter.log("BiometricCredentialStore.enable failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        CrashReporter.recordException(throwable)
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
