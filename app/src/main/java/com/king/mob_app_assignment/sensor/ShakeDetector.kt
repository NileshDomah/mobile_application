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

// Pocket-guard strategy: a real shake produces several direction reversals (back-and-forth
// motion) close together in time. Each qualifying reversal refreshes a rolling deadline
// (lastReversalTime + WINDOW_MS) rather than resetting on a fixed wall-clock tick, so reversals
// that happen to straddle an arbitrary window boundary are no longer discarded mid-shake - that
// was the previous implementation's bug (a real shake could silently fail to register depending
// on exactly when, relative to a fixed 1-second clock tick, the user started shaking).
//
// The magnitude gate for counting a reversal is now the SAME threshold used for the final
// trigger check (previously half-threshold for counting vs full-threshold for triggering, an
// inconsistency that made the two conditions fight each other). A separate, deliberately
// unreliable hardware signal - proximity sensor state - is no longer used as a gate at all: on
// many real devices it reports stale/default "near" readings that don't reflect whether the
// phone is actually in a pocket, which could permanently block shake detection depending on the
// phone and how it's held. The manual "Report an Issue" button remains the reliable fallback
// entry point regardless of how shake behaves on a given device.
@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shakePreferences: ShakePreferences,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastDelta = 0f
    private var reversalCount = 0
    private var lastReversalTime = 0L
    private var lastShakeTime = 0L

    private var thresholdMs2 = DEFAULT_THRESHOLD

    private var onShake: (() -> Unit)? = null

    fun onShakeDetected(): Flow<Unit> = callbackFlow {
        thresholdMs2 = shakePreferences.thresholdFlow.first()
        onShake = { trySend(Unit) }

        accelerometer?.let {
            sensorManager.registerListener(this@ShakeDetector, it, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            sensorManager.unregisterListener(this@ShakeDetector)
            onShake = null
        }
    }

    fun updateThreshold(value: Float) {
        thresholdMs2 = value
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        val delta = (gForce - 1f) * SensorManager.GRAVITY_EARTH

        val now = System.currentTimeMillis()

        // Rolling window: if too long has passed since the last qualifying reversal, the shake
        // attempt has gone stale and we start counting fresh instead of accumulating forever.
        if (lastReversalTime != 0L && now - lastReversalTime > WINDOW_MS) {
            reversalCount = 0
        }

        if (kotlin.math.abs(delta) > thresholdMs2) {
            val sameSign = (delta >= 0f) == (lastDelta >= 0f)
            if (!sameSign) {
                reversalCount++
                lastReversalTime = now
            }
            lastDelta = delta
        }

        val reversalOk = reversalCount >= MIN_REVERSALS

        if (reversalOk && now - lastShakeTime > DEBOUNCE_MS) {
            lastShakeTime = now
            reversalCount = 0
            lastReversalTime = 0L
            onShake?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val WINDOW_MS = 1000L
        const val DEBOUNCE_MS = 1500L
        const val MIN_REVERSALS = 2
        const val DEFAULT_THRESHOLD = 15f
    }
}
