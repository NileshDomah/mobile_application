package com.king.mob_app_assignment.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether MainActivity's own (foreground) shake listener is currently active, so
 * [ShakeBackgroundService] can stop its own sensor registration while it is.
 *
 * FIX (2026-09-11, user-reported "shaking opens the camera twice, I can see the screen change
 * twice"): MainActivity's foreground listener and ShakeBackgroundService's background listener
 * were BOTH always active at the same time whenever "shake in background" was turned on - a
 * previous concurrency fix (see ShakeDetector's own class doc) made sure that didn't corrupt
 * shared detection state, but it never addressed the visible side effect: a single physical shake
 * gets detected independently by BOTH listeners, and each one reacts on its own.
 *
 * ROOT-CAUSE FIX (2026-10-08, same symptom persisting after several narrower attempts): every
 * previous fix here tried to filter the EFFECT of a shake after both listeners had already
 * independently detected it (a "grace window" suppressing navigation/the capture launch for a
 * moment). Live logcat evidence finally confirmed the real bug: ShakeBackgroundService.onCreate()
 * unconditionally registers its own ShakeDetector collection via startForeground() the moment
 * "Shake in Background" is turned on, with NO check for whether the app is already open and
 * foregrounded at that moment - HomeScreen's own toggle calls startShakeService() regardless of
 * whether MainActivity is currently showing. That means the background service and MainActivity's
 * foreground LifecycleStartEffect collector can BOTH be actively registered against the real
 * accelerometer at the same time, continuously, not just in some narrow race window - confirmed by
 * logcat showing two independent "onShakeDetected: starting collection" lines on two different
 * threads within 50ms of each other at ordinary app startup, and two "SHAKE TRIGGERED" lines for
 * one physical gesture. No grace window applied AFTER two independently-registered sensor
 * listeners both fire can ever fully fix that - the actual registration itself needs to be
 * exclusive. isForeground is now a StateFlow instead of a plain AtomicBoolean so
 * ShakeBackgroundService can OBSERVE transitions live and stop/restart its own sensor
 * registration in response, rather than only reading a snapshot value after the fact.
 */
object ShakeForegroundState {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun setForeground(foreground: Boolean) {
        android.util.Log.d("ShakeDebug", "setForeground($foreground)")
        _isForeground.value = foreground
    }

    fun isCurrentlyForeground(): Boolean = _isForeground.value
}
