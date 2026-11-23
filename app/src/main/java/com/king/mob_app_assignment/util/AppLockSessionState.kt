package com.king.mob_app_assignment.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks whether the App Lock PIN/biometric gate has already been satisfied for the current
 * "session", where a session now means a TIMED window of real backgrounding, not merely "until
 * MainActivity is destroyed and recreated."
 *
 * BUG FIX (2026-10-08, user-reported "if I go back from the camera it asks for PIN every time"):
 * this flag previously lived as `remember { mutableStateOf(false) }` directly inside
 * MainActivity's RootNavGraph composable - Activity-instance-scoped state, not process-scoped as
 * its old comment intended. Launching the system camera (CaptureScreen's TakePicture contract)
 * starts a separate, memory-heavy foreground activity on top of MainActivity; Android frequently
 * reclaims a backgrounded Activity's process under the memory pressure a real camera app causes,
 * especially on mid/low-end devices. When the user then pressed back, MainActivity was recreated
 * from scratch, its remember{} block re-initialised to false, and the PIN screen reappeared even
 * though the user never really left the app's logical session - they just took a photo. Moving
 * this flag to a singleton object (same pattern as ShakeForegroundState) fixes that, since a
 * singleton survives Activity recreation within the same process.
 *
 * FEATURE (same date, explicit user request): "I want the pin to have like sessions. If the app
 * wasn't closed don't ask, or if the app has been in background for too long then ask." Plain
 * Activity-survival alone would satisfy "brief camera round-trip, don't ask again," but not the
 * other half of the request - a session that was backgrounded for a long time (switched away to
 * another app and left it there, or the OS fully killed the process and relaunched it later)
 * should still re-lock. ProcessLifecycleOwner (unlike MainActivity's own onStart/onStop, which
 * only tracks THIS Activity) observes the whole app's foreground/background transitions across
 * every Activity, so a DefaultLifecycleObserver registered on it here stamps lastBackgroundedAtMs
 * whenever the entire app (not just one Activity) leaves the foreground, and clears the unlocked
 * flag on the next resume if that gap exceeded BACKGROUND_TIMEOUT_MS - otherwise leaves it alone,
 * so a brief camera trip (well under the timeout) is never re-locked.
 */
object AppLockSessionState {
    private val isUnlockedThisSession = AtomicBoolean(false)
    private val lastBackgroundedAtMs = AtomicLong(0L)
    private var observerRegistered = false

    // FEATURE (explicit user request, 2026-11-18): "open camera [on shake], then after photo OK
    // ask for PIN" - a background shake used to be silently swallowed if the app-lock session had
    // timed out (60s+ since last use): MainActivity correctly navigated to the capture screen
    // underneath, but AppLockScreen's overlay was drawn on top of it at the same time, so the user
    // saw the PIN screen flash and then nothing, with the pending capture already consumed with
    // no visible camera launch. This flag lets a shake-triggered launch suppress that overlay
    // specifically while a capture is pending/in progress, matching the system keyguard already
    // being bypassed for this exact flow (see MainActivity.allowShowingOverLockScreenForShakeCapture)
    // - the app's own PIN is asked for afterward instead, once the photo is actually captured (see
    // CaptureScreen's own RequireDeviceUnlockOncePhotoCaptured for the same timing pattern applied
    // to the system keyguard).
    private val shakeCaptureInProgress = AtomicBoolean(false)

    fun markShakeCaptureStarted() {
        shakeCaptureInProgress.set(true)
    }

    fun markShakeCaptureFinished() {
        shakeCaptureInProgress.set(false)
    }

    fun isShakeCaptureInProgress(): Boolean = shakeCaptureInProgress.get()

    // How long the whole app can sit backgrounded before the next resume re-locks it. Long enough
    // that a camera round-trip, a quick notification-shade glance, or briefly switching to another
    // app and straight back never re-prompts; short enough that leaving the app for a real break
    // (a meeting, overnight) does.
    private const val BACKGROUND_TIMEOUT_MS = 60_000L

    fun isUnlocked(): Boolean = isUnlockedThisSession.get()

    fun markUnlocked() {
        isUnlockedThisSession.set(true)
    }

    /** Called by the sign-out path, so a fresh sign-in always re-locks rather than inheriting a stale unlocked flag from the session that just ended. */
    fun reset() {
        isUnlockedThisSession.set(false)
        lastBackgroundedAtMs.set(0L)
    }

    /**
     * Registers the app-wide backgrounding observer exactly once per process. Safe to call
     * repeatedly (e.g. from every MainActivity.onCreate) - only the first call actually attaches
     * the observer, so recreating MainActivity never double-registers it.
     */
    fun ensureObserverRegistered() {
        if (observerRegistered) return
        observerRegistered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Whole app (every Activity) left the foreground - stamp the moment, not yet a
                // decision either way; that's made on the NEXT onStart, once we know how long it
                // lasted.
                lastBackgroundedAtMs.set(System.currentTimeMillis())
            }

            override fun onStart(owner: LifecycleOwner) {
                val backgroundedAt = lastBackgroundedAtMs.get()
                if (backgroundedAt != 0L && System.currentTimeMillis() - backgroundedAt > BACKGROUND_TIMEOUT_MS) {
                    isUnlockedThisSession.set(false)
                }
            }
        })
    }
}
