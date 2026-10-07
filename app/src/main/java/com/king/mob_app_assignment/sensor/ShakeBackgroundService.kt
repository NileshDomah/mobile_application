package com.king.mob_app_assignment.sensor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.king.mob_app_assignment.MainActivity
import com.king.mob_app_assignment.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// Lets shake-to-report work even when the app isn't open. Android forbids silent background
// sensor listening, so this must be a genuine foreground service with a persistent notification
// while active - the user explicitly opts in via the Settings toggle (see
// ShakePreferences.backgroundShakeEnabledFlow), and can stop it from there or by swiping away the
// notification. Reuses the exact same ShakeDetector singleton MainActivity uses in the foreground,
// so shake sensitivity/detection logic is defined in exactly one place.
@AndroidEntryPoint
class ShakeBackgroundService : Service() {

    @Inject lateinit var shakeDetector: ShakeDetector

    private var job: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        job = serviceScope.launch {
            // Lower-power sampling rate than the foreground in-app listener - see
            // ShakeDetector's class doc. This service can run indefinitely in the background, so
            // the reduced CPU/battery cost of SENSOR_DELAY_NORMAL matters here in a way it
            // doesn't for the foreground case.
            shakeDetector.onShakeDetected(sensorDelay = SensorManager.SENSOR_DELAY_NORMAL).collectLatest {
                // See ShakeForegroundState's own doc comment ("shake opens the camera twice") -
                // MainActivity's foreground listener already handles a shake while the app is
                // actually in the foreground; reacting here too, on the exact same physical
                // shake, was what caused the double navigation.
                if (!ShakeForegroundState.isCurrentlyForeground()) {
                    // Stamped BEFORE startActivity() brings MainActivity (and its own foreground
                    // shake listener) back to life - see ShakeForegroundState's own doc comment
                    // for why this is needed on top of the isForeground flag alone.
                    ShakeForegroundState.recordBackgroundTrigger()
                    launchCaptureFromBackground()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchCaptureFromBackground() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_LAUNCH_CAPTURE, true)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Shake to Report is listening")
            .setContentText("Shake your phone anywhere to report a civic issue instantly.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "background_shake_listener"
        private const val NOTIFICATION_ID = 42
    }
}
