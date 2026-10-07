package com.king.mob_app_assignment.util

/**
 * A tiny in-memory (never persisted, never encrypted-at-rest) relay for the plaintext email and
 * password from a login/register call that JUST succeeded, so SetupAppLockScreen can use them to
 * enroll biometric login (BiometricCredentialStore.enable() needs the real password - see its
 * own doc comment for why that store keeps a reversible copy while AppPinStore never does).
 *
 * This is the ONLY place a plaintext password briefly exists outside of BiometricPrompt's own
 * challenge or the moment it's typed into LoginScreen/RegisterScreen - it's set once by
 * LoginViewModel/RegisterViewModel right on their own success path, consumed exactly once by
 * RootNavGraph the moment it renders SetupAppLockScreen, and never touched again. A process
 * being killed between those two moments simply loses it (a plain object, not backed by any
 * persistence) - RootNavGraph's own null-check on consume() handles that by signing the user
 * back out rather than getting stuck, see its own doc comment.
 *
 * TIMING (2026-09-10, root cause of a real "can't login now, missing or insufficient
 * permissions" bug): set() MUST be called before authRepository.login()/register(), not
 * afterward from their .onSuccess{} callback. Firebase notifies every registered
 * AuthStateListener - including MainActivity's own, entirely separate one - the instant its
 * internal auth state actually changes, which can happen before the specific coroutine that
 * called login()/register() has even resumed past its own await() to reach a line after it.
 * Calling set() only in .onSuccess{} left a real window where MainActivity's listener could see
 * the user as logged in and call consume() before set() had ever run - seeing null on a
 * genuinely successful login, every time, and incorrectly treating it as "no credentials, sign
 * back out." See LoginViewModel.login()/RegisterViewModel's register flow for where this is
 * actually called from now.
 */
object PendingAppLockCredentials {
    data class Credentials(val email: String, val password: String)

    private var pending: Credentials? = null

    fun set(email: String, password: String) {
        pending = Credentials(email, password)
    }

    /** Reads and immediately clears the held credentials - never returns the same value twice. */
    fun consume(): Credentials? {
        val value = pending
        pending = null
        return value
    }
}
