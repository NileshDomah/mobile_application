package com.king.mob_app_assignment.sensor

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks whether MainActivity's own (foreground) shake listener is currently active, so
 * [ShakeBackgroundService] can ignore shakes while it is - see that service's own doc comment.
 *
 * FIX (2026-09-11, user-reported "shaking opens the camera twice, I can see the screen change
 * twice"): MainActivity's foreground listener and ShakeBackgroundService's background listener
 * were BOTH always active at the same time whenever "shake in background" was turned on - a
 * previous concurrency fix (see ShakeDetector's own class doc) made sure that didn't corrupt
 * shared detection state, but it never addressed the visible side effect: a single physical shake
 * gets detected independently by BOTH listeners, and each one reacts on its own - MainActivity
 * navigates to Capture in-place, while the background service separately calls startActivity()
 * with EXTRA_LAUNCH_CAPTURE, re-entering/relaunching the same Activity a second time. That's
 * exactly "the screen changing twice."
 *
 * SECOND FIX (2026-10-01, user-reported the exact same symptom again - "camera opens, then I see
 * another popup appear"): the isForeground flag alone wasn't enough on its own. The actual
 * sequence was: a shake happens while the app is genuinely backgrounded, so isForeground is false
 * and ShakeBackgroundService correctly fires launchCaptureFromBackground(). That call starts
 * MainActivity via startActivity() - which, now that MainActivity's process/Activity is actually
 * coming to the foreground, immediately re-runs its own LifecycleStartEffect and registers a
 * brand-new foreground ShakeDetector collector, setting isForeground to true. But a real shake
 * gesture takes a second or more to complete and lives well within ShakeDetector's own rolling
 * detection window (WINDOW_MS) - so the SAME physical shake that just triggered the background
 * service can still be "in progress" from the sensor's point of view the instant this new
 * foreground collector starts listening, and that collector then independently detects and fires
 * its own second shake for the same gesture, navigating to Capture a second time on top of the
 * first. Fixed with a short cooldown: whenever the background service is the one that reacts, it
 * stamps lastBackgroundTriggerAtMs, and the newly-started foreground listener checks this and
 * skips re-arming its own detection for a brief grace window - long enough for an already-detected
 * shake gesture to finish decaying out of the sensor's own recent-motion state, but far too short
 * to ever suppress a second, genuinely new shake the user deliberately performs afterward.
 *
 * A plain AtomicBoolean/AtomicLong, not a StateFlow/DataStore - this is pure in-process signaling
 * between two components in the same app process (MainActivity and ShakeBackgroundService always
 * run in the same process here), updated on the UI thread from MainActivity's own onStart/onStop
 * and read from the background service's sensor callback thread, hence Atomic rather than plain
 * fields.
 */
object ShakeForegroundState {
    private val isForeground = AtomicBoolean(false)
    private val lastBackgroundTriggerAtMs = AtomicLong(0L)

    // Must comfortably exceed ShakeDetector's own WINDOW_MS (1500ms) - that's how long a shake
    // gesture's own detection state can still be "live" after the moment it first crossed
    // threshold, so the grace window here needs to outlast it, not just match it.
    private const val POST_BACKGROUND_TRIGGER_GRACE_MS = 2500L

    fun setForeground(foreground: Boolean) {
        isForeground.set(foreground)
    }

    fun isCurrentlyForeground(): Boolean = isForeground.get()

    /** Called by ShakeBackgroundService right before it launches Capture from a background shake. */
    fun recordBackgroundTrigger() {
        lastBackgroundTriggerAtMs.set(System.currentTimeMillis())
    }

    /**
     * True for a brief window after a background-triggered shake, so the foreground listener that
     * is about to start up (because the background trigger is itself what's bringing the app to
     * the foreground) knows to ignore detection results for a moment rather than re-triggering on
     * the tail end of the same physical gesture.
     */
    fun isWithinPostBackgroundTriggerGrace(): Boolean {
        val last = lastBackgroundTriggerAtMs.get()
        return last != 0L && System.currentTimeMillis() - last <= POST_BACKGROUND_TRIGGER_GRACE_MS
    }
}
