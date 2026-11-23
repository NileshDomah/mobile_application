package com.king.mob_app_assignment.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

// Detection strategy: a real shake produces several rapid pulses of acceleration-magnitude
// spiking above threshold and dropping back down, close together in time. Each qualifying
// threshold-crossing (rising edge) refreshes a rolling deadline (lastReversalTime + WINDOW_MS)
// rather than resetting on a fixed wall-clock tick, so pulses that happen to straddle an
// arbitrary window boundary are no longer discarded mid-shake - that was a previous
// implementation's bug (a real shake could silently fail to register depending on exactly when,
// relative to a fixed 1-second clock tick, the user started shaking).
//
// ALGORITHM FIX (2026-09-11, "shaking isn't working at all" on a real device, confirmed via live
// logcat): this used to count a "reversal" only when the SIGN of delta flipped between samples
// above threshold. That's broken by construction - delta is derived from gForce, a vector
// MAGNITUDE (sqrt of squares), which is mathematically non-negative, so delta is overwhelmingly
// positive during any real shake and its sign essentially never flips. Live logcat on a real
// device showed delta spiking past 99 (threshold ~10) during a deliberate shake with zero
// "reversal" events ever counted - the shake was real and strong, the sensor was reporting it
// correctly, but the sign-flip condition could only ever be satisfied once, if at all. Replaced
// with threshold-CROSSING counting (each rising edge from at-or-below to above threshold counts
// as one pulse) - this is what actually happens repeatedly during a real shake gesture and
// requires no assumption about delta's sign.
//
// The magnitude gate for counting a pulse is the SAME threshold used for the final trigger check
// (previously half-threshold for counting vs full-threshold for triggering, an inconsistency that
// made the two conditions fight each other).
//
// Pocket check (2026-08, re-added on explicit request after an earlier removal - see git history
// / PLACEHOLDERS_TODO.csv): gates a detected shake on the proximity sensor reading "far" (not
// covered) at the moment the shake completes. The PREVIOUS implementation's real bug wasn't the
// sensor itself - it's that some devices report a stale/default "near" value that never updates
// again after the first reading, which under a naive "block whenever proximity == NEAR" rule
// could brick shake detection permanently on those devices. This version only trusts a proximity
// reading while it's FRESH (updated within PROXIMITY_STALENESS_MS - see onAccuracyChanged/
// onSensorChanged's proximity branch, which stamps lastProximityUpdateTime on every real
// callback); a stale or never-received reading is treated as "unknown, don't block" rather than
// as an implicit veto, so a device with a broken/absent proximity sensor still behaves exactly
// like before this feature existed. The manual "Report an Issue" button remains the reliable
// fallback entry point regardless of how shake or the pocket check behaves on a given device.
//
// Reliability improvement: raw accelerometer readings are noisy at any register-listener rate,
// and that noise was previously fed straight into the reversal-detection delta - a burst of
// high-frequency jitter (walking, a bumpy pocket, a phone laid on a vibrating surface) could
// register spurious sign-reversals of its own. A single-pole exponential low-pass filter
// (LOW_PASS_ALPHA) is applied to each axis before computing gForce, so only sustained,
// deliberate motion (a real shake) survives into the delta the reversal-counter actually sees -
// high-frequency noise is smoothed out rather than being treated as a candidate reversal.
//
// Efficiency improvement: the sensor is registered at a caller-chosen sampling delay rather than
// a single hardcoded rate - the foreground (in-app) listener uses SENSOR_DELAY_GAME for
// responsiveness. ShakeBackgroundService originally used SENSOR_DELAY_NORMAL (~5Hz) to save
// battery, but that turned out to be too slow to reliably catch a real shake gesture's actual
// peaks (confirmed via dumpsys sensorservice - see ShakeBackgroundService's own 2026-11-18 fix
// note) and now uses SENSOR_DELAY_UI (~16Hz) instead - still lower-power than the foreground
// case, but fast enough to actually detect the shake it's meant to.
//
// Concurrency fix (2026-09): this used to be the SensorEventListener itself, with all detection
// state (onShake callback, filter baseline, reversal counters, proximity state) stored directly
// as fields on the singleton. That silently broke whenever two callers collected
// onShakeDetected() at the same time - which happens routinely, since MainActivity's foreground
// listener and ShakeBackgroundService's background listener are BOTH allowed to be active
// together (e.g. background-shake is enabled, then the user reopens the app while the service is
// still running). The second collector's registerListener() call and its `onShake = { ... }`
// assignment would silently clobber the first collector's state on the same shared instance, and
// either collector stopping (e.g. the app backgrounding again) would unregister the ONE shared
// listener out from under the other - so shake could silently stop delivering events to whichever
// side lost the race, with no crash or visible symptom. Fixed by making each onShakeDetected()
// call create its own private ShakeSession (its own SensorEventListener + its own detection
// state), so multiple concurrent collectors never share mutable state and never fight over a
// single registerListener/unregisterListener pair.
@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shakePreferences: ShakePreferences,
) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    /**
     * @param sensorDelay one of the `SensorManager.SENSOR_DELAY_*` constants - callers pick the
     * sampling rate appropriate to their context (see class doc: foreground vs background).
     */
    fun onShakeDetected(sensorDelay: Int = SensorManager.SENSOR_DELAY_GAME): Flow<Unit> = callbackFlow {
        val threshold = shakePreferences.thresholdFlow.first()
        android.util.Log.d(
            "ShakeDetector",
            "onShakeDetected: starting collection, threshold=$threshold, " +
                "accelerometer=${accelerometer != null}, proximitySensor=${proximitySensor != null}",
        )
        val session = ShakeSession(threshold) {
            android.util.Log.d("ShakeDetector", "SHAKE TRIGGERED - calling onShake()")
            trySend(Unit)
        }

        accelerometer?.let {
            val registered = sensorManager.registerListener(session, it, sensorDelay)
            android.util.Log.d("ShakeDetector", "registerListener(accelerometer) returned $registered")
        }
        // Proximity readings are cheap/rare (a near-binary sensor, not a continuous stream like
        // the accelerometer) - always registered at the slowest delay regardless of the caller's
        // chosen accelerometer rate, since there's no responsiveness benefit to sampling it faster.
        proximitySensor?.let {
            sensorManager.registerListener(session, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        awaitClose {
            android.util.Log.d("ShakeDetector", "onShakeDetected: collection stopped, unregistering")
            sensorManager.unregisterListener(session)
        }
    }

    // Owns exactly one collector's worth of detection state - a fresh instance per
    // onShakeDetected() call/collection, never shared, so concurrent foreground/background
    // listeners can't corrupt each other (see class doc's Concurrency fix note).
    private class ShakeSession(
        @Volatile var thresholdMs2: Float,
        private val onShake: () -> Unit,
    ) : SensorEventListener {

        // Null until the first real proximity callback arrives - distinct from "far" so a device
        // that never delivers one (missing sensor, or Android never calls back before a shake
        // completes) is unambiguously "unknown", not treated as pocketed.
        private var isNear: Boolean? = null
        private var lastProximityUpdateTime = 0L

        private var filteredX = 0f
        private var filteredY = 0f
        private var filteredZ = 0f
        private var hasFilterBaseline = false

        private var reversalCount = 0
        private var lastReversalTime = 0L
        private var lastShakeTime = 0L

        // Was the last sample's delta ABOVE thresholdMs2? Drives edge-detection below - see the
        // 2026-09-11 fix note where this replaced the previous, broken sign-based approach.
        private var wasAboveThreshold = false

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                // Most proximity sensors report a raw distance in cm, with "far" being the
                // sensor's own maximumRange (a phone-in-hand/on-a-table reading) and any smaller
                // value meaning something is close against it. Comparing against maximumRange
                // directly (rather than an arbitrary fixed cm threshold) works across the
                // different ranges real devices report.
                isNear = event.values.getOrNull(0)?.let { it < (event.sensor.maximumRange - 0.01f) } ?: false
                lastProximityUpdateTime = System.currentTimeMillis()
                return
            }
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val rawX = event.values[0]
            val rawY = event.values[1]
            val rawZ = event.values[2]

            if (!hasFilterBaseline) {
                // First sample after registering: seed the filter directly rather than smoothing
                // from a zero baseline, which would otherwise produce one large, spurious
                // filtered delta on the very first reading.
                filteredX = rawX
                filteredY = rawY
                filteredZ = rawZ
                hasFilterBaseline = true
            } else {
                filteredX += LOW_PASS_ALPHA * (rawX - filteredX)
                filteredY += LOW_PASS_ALPHA * (rawY - filteredY)
                filteredZ += LOW_PASS_ALPHA * (rawZ - filteredZ)
            }

            val gX = filteredX / SensorManager.GRAVITY_EARTH
            val gY = filteredY / SensorManager.GRAVITY_EARTH
            val gZ = filteredZ / SensorManager.GRAVITY_EARTH
            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
            val delta = (gForce - 1f) * SensorManager.GRAVITY_EARTH

            val now = System.currentTimeMillis()

            // Rolling window: if too long has passed since the last qualifying reversal, the
            // shake attempt has gone stale and we start counting fresh instead of accumulating
            // forever.
            if (lastReversalTime != 0L && now - lastReversalTime > WINDOW_MS) {
                reversalCount = 0
            }

            // BUG FIX (2026-09-11, confirmed via live logcat on a real device - "shaking isn't
            // working at all"): gForce is sqrt(gX^2+gY^2+gZ^2), a MAGNITUDE - it is mathematically
            // never negative, so delta = (gForce-1)*g is overwhelmingly positive during a real
            // shake (it only goes negative in the brief instants total acceleration magnitude
            // dips below 1g, which a violent shake barely produces, if at all). The previous
            // logic counted a "reversal" only when delta's SIGN flipped relative to the last
            // sample above threshold - since delta almost never goes meaningfully negative, that
            // condition was true on the very first crossing and then stayed permanently false for
            // the rest of a real shake, so reversalCount could never reach MIN_REVERSALS. Live
            // logcat on this exact device showed delta spiking as high as 99 (far above the
            // threshold of ~10) during a real, deliberate shake, yet zero "reversal" log lines
            // ever printed - proving this was the actual root cause, not a sensitivity/filter
            // tuning problem.
            //
            // Fixed by counting THRESHOLD CROSSINGS (rising edges) instead of sign flips: each
            // time delta rises from at-or-below threshold to above threshold counts as one pulse
            // of shake motion - exactly what happens repeatedly, in quick succession, during a
            // real back-and-forth shake gesture, regardless of how asymmetric delta's sign is.
            val isAboveThreshold = delta > thresholdMs2
            if (isAboveThreshold && !wasAboveThreshold) {
                reversalCount++
                lastReversalTime = now
            }
            wasAboveThreshold = isAboveThreshold

            val reversalOk = reversalCount >= MIN_REVERSALS

            if (reversalOk && now - lastShakeTime > DEBOUNCE_MS && !isPocketed(now)) {
                lastShakeTime = now
                reversalCount = 0
                lastReversalTime = 0L
                onShake()
            }
        }

        // See the class doc comment for why this only trusts a FRESH proximity reading: a stale
        // one (older than PROXIMITY_STALENESS_MS) or the complete absence of one (isNear == null
        // - no proximity sensor, or Android simply hasn't delivered a callback yet) is treated as
        // "unknown," which returns false here rather than blocking the shake.
        private fun isPocketed(now: Long): Boolean {
            val near = isNear ?: return false
            val isFresh = lastProximityUpdateTime != 0L && now - lastProximityUpdateTime <= PROXIMITY_STALENESS_MS
            return near && isFresh
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private companion object {
        // Widened from 1000ms (2026-09-11, user-reported "shaking isn't working at all" on a
        // physical device) - alongside adding the HIGH_SAMPLING_RATE_SENSORS manifest permission
        // (see AndroidManifest.xml's own comment: without it, Android 12+ silently caps
        // accelerometer delivery well below the SENSOR_DELAY_GAME rate this class requests, with
        // no error/crash, just far fewer onSensorChanged callbacks per second than expected). A
        // slower-than-requested delivery rate means fewer chances to observe a reversal within
        // any fixed window - widening the window compensates directly, at the cost of a real
        // shake needing to stay "in progress" slightly longer before the first two reversals
        // both land inside it, which is a good trade against the alternative of shake silently
        // not working at all on a throttled device.
        const val WINDOW_MS = 1500L
        const val DEBOUNCE_MS = 1500L

        // Raised 2 -> 3 (2026-11-20, user-reported "I just moved the phone a little and it opened
        // the camera" - ordinary handling like picking the phone up off a table, not a deliberate
        // shake). 2 threshold-crossings within WINDOW_MS is also what a single abrupt pickup
        // produces (one crossing on the initial jerk, a second on the moment it settles/stops) -
        // nothing about that sequence is specific to shaking. A real shake gesture is a repeated
        // back-and-forth motion, so it keeps crossing the threshold several times within the same
        // window; requiring a third crossing filters out single-jerk handling while still letting
        // an actual shake (which produces many crossings, not just two) through easily.
        const val MIN_REVERSALS = 3

        // Smoothing factor for the exponential low-pass filter, 0..1 - lower values smooth more
        // aggressively (more noise rejection, slightly more lag before a real shake registers).
        //
        // Raised 0.35 -> 0.6 (2026-09-11, alongside ShakePreferences.DEFAULT_THRESHOLD 15->10 -
        // see that file's comment for the full root-cause writeup). 0.35 smoothed away too much
        // of a real shake's own peak amplitude on real hardware - a real accelerometer's actual
        // shake signal was being filtered down below the trigger threshold, not just its noise.
        // 0.6 lets much more of the raw signal through (less lag, less attenuation of genuine
        // motion) while still rejecting the highest-frequency single-sample jitter.
        const val LOW_PASS_ALPHA = 0.6f

        // How old a proximity reading can be and still be trusted to block a shake. Generous on
        // purpose (SENSOR_DELAY_NORMAL callbacks arrive roughly every 200ms in practice, but some
        // devices only re-report on a state CHANGE, not periodically) - the goal is "was this
        // sensor demonstrably alive recently," not tight real-time tracking, since a stale
        // reading is treated as "unknown" (not pocketed) rather than as a block either way.
        const val PROXIMITY_STALENESS_MS = 4_000L
    }
}
