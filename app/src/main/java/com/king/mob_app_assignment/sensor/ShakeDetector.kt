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

// Pocket-guard strategy: a real shake produces several consecutive samples above threshold with
// alternating sign of the linear-acceleration delta (back-and-forth motion). A single jostle or a
// phone sliding in a pocket typically produces one spike without reversals, or a sustained reading
// with no reversal at all, so we require at least MIN_REVERSALS direction changes within the
// sampling window before firing, in addition to the raw magnitude threshold.
@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shakePreferences: ShakePreferences,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastDelta = 0f
    private var reversalCount = 0
    private var windowStartTime = 0L
    private var lastShakeTime = 0L
    private var isProximityCovered = false

    private var thresholdMs2 = DEFAULT_THRESHOLD

    private var onShake: (() -> Unit)? = null

    fun onShakeDetected(): Flow<Unit> = callbackFlow {
        thresholdMs2 = shakePreferences.thresholdFlow.first()
        onShake = { trySend(Unit) }

        val proximityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                isProximityCovered = event.values.isNotEmpty() &&
                    event.values[0] < (proximitySensor?.maximumRange ?: 5f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        accelerometer?.let {
            sensorManager.registerListener(this@ShakeDetector, it, SensorManager.SENSOR_DELAY_GAME)
        }
        proximitySensor?.let {
            sensorManager.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        awaitClose {
            sensorManager.unregisterListener(this@ShakeDetector)
            sensorManager.unregisterListener(proximityListener)
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

        if (windowStartTime == 0L || now - windowStartTime > WINDOW_MS) {
            windowStartTime = now
            reversalCount = 0
            lastDelta = delta
        }

        if (kotlin.math.abs(delta) > thresholdMs2 / 2f) {
            val sameSign = (delta >= 0f) == (lastDelta >= 0f)
            if (!sameSign) {
                reversalCount++
            }
            lastDelta = delta
        }

        val magnitudeOk = kotlin.math.abs(delta) > thresholdMs2
        val pocketGuardOk = !isProximityCovered
        val reversalOk = reversalCount >= MIN_REVERSALS

        if (magnitudeOk && pocketGuardOk && reversalOk && now - lastShakeTime > DEBOUNCE_MS) {
            lastShakeTime = now
            reversalCount = 0
            windowStartTime = 0L
            onShake?.invoke()
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val WINDOW_MS = 1000L
        const val DEBOUNCE_MS = 1500L
        const val MIN_REVERSALS = 2
        const val DEFAULT_THRESHOLD = 15f
    }
}
