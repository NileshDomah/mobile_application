package com.king.mob_app_assignment.sensor

import android.app.ActivityOptions
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
import com.king.mob_app_assignment.util.ShakePreferences
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
//
// ROOT-CAUSE FIX (2026-10-08, "shake still opens camera twice" persisting after several narrower
// attempts): live logcat finally confirmed this service used to keep its own sensor registration
// running UNCONDITIONALLY the entire time "Shake in Background" is enabled, including while the
// app is already open and foregrounded - HomeScreen's toggle starts this service regardless of
// whether MainActivity is currently showing, and nothing here ever stopped listening just because
// the app came to the foreground. That meant this service's ShakeDetector collector and
// MainActivity's own foreground collector were BOTH genuinely, continuously registered against
// the real accelerometer at the same time, not just colliding in some narrow timing race -
// confirmed by two independent "onShakeDetected: starting collection" log lines firing within
// 50ms of each other at ordinary app startup. Every previous fix here tried to filter the EFFECT
// of a shake after both listeners had already independently detected it; that can never fully
// work when the root problem is dual sensor registration itself. This service now actively
// starts/stops its OWN sensor collection in response to ShakeForegroundState.isForeground
// transitioning, so only one listener (foreground XOR background) is ever actually registered
// against the sensor at a given moment - see startOrStopListening() below.
@AndroidEntryPoint
class ShakeBackgroundService : Service() {

    @Inject lateinit var shakeDetector: ShakeDetector
    @Inject lateinit var shakePreferences: ShakePreferences

    private var foregroundWatcherJob: Job? = null
    private var preferenceWatcherJob: Job? = null
    private var detectionJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob())

    // BUG FIX (2026-11-20, "quick flash of the app on lock screen, then it closes" - confirmed
    // via logcat: one physical shake while locked produced 15-20 "background onShakeDetected
    // fired" events within ~5 seconds, each independently calling startActivity(), each
    // interrupting the previous one's onCreate/window-attach before it could finish - visible as
    // rapid setForeground(true)->(false) flips with the capture-navigation LaunchedEffect never
    // getting a stable moment to run). ShakeDetector's own internal DEBOUNCE_MS=1500 lives inside
    // a per-collection ShakeSession and gets reset to a fresh lastShakeTime=0 every time a brand
    // new ShakeSession is created - exactly what isForeground.collectLatest below does on every
    // true/false flap, which this very thrashing itself causes (a feedback loop: flap -> fresh
    // session -> immediately re-detects the still-ongoing shake -> launches again -> more
    // flapping). A debounce HERE, independent of ShakeSession's internal state, breaks that loop
    // regardless of why the thrashing happens, by simply refusing to call
    // launchCaptureFromBackground() more than once per window.
    private var lastBackgroundLaunchAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        android.util.Log.d(DEBUG_TAG, "onCreate: isForeground=${ShakeForegroundState.isCurrentlyForeground()}")

        // BUG FIX (2026-11-20, "toggle off, yet shake still opens camera while app is minimized"):
        // START_STICKY (see onStartCommand) means Android can recreate this service on its own,
        // with no Intent at all, after killing it for resource pressure - entirely independent of
        // HomeScreen/SettingsScreen's stopService() call when the user flips the toggle off. This
        // service used to never re-check the live preference after that point, so a system-
        // triggered restart would silently resume listening even though the user had explicitly
        // turned the feature off. Watching the flow here means ANY transition to disabled - a
        // user toggle while already running, or just the live preference value at a restart -
        // stops the service outright, not merely its sensor collection.
        preferenceWatcherJob = serviceScope.launch {
            shakePreferences.backgroundShakeEnabledFlow.collectLatest { enabled ->
                android.util.Log.d(DEBUG_TAG, "backgroundShakeEnabledFlow collected: $enabled")
                if (!enabled) {
                    stopSelf()
                }
            }
        }
        // BUG FIX (found via logcat on a real device, 2026-11-17): the old version only ever
        // learned the real isForeground value through collectLatest inside serviceScope.launch{} -
        // launching a coroutine just SCHEDULES it on a dispatcher, it does not run synchronously,
        // so there was a real (observed ~37ms here, but not bounded) window right at service
        // startup where this service had already called startListening() and begun registering
        // its own sensor collection, before the launched coroutine got around to collecting
        // isForeground's actual current value. A shake landing inside that window could be caught
        // by BOTH this service's listener and MainActivity's own foreground listener - exactly
        // "one shake, camera opens twice," reported again after the original 2026-10-08 fix for
        // the same symptom. Checking the current value synchronously here, before ever deciding
        // whether to start listening, closes that window; the collector below still exists to
        // react to every LATER transition.
        if (!ShakeForegroundState.isCurrentlyForeground()) {
            startListening()
        }
        foregroundWatcherJob = serviceScope.launch {
            ShakeForegroundState.isForeground.collectLatest { isForeground ->
                android.util.Log.d(DEBUG_TAG, "isForeground collected: $isForeground")
                if (isForeground) {
                    // The app itself is now foregrounded and has its own live listener - stop
                    // ours entirely rather than merely ignoring its results, so the two can never
                    // both be registered against the real sensor at the same time.
                    detectionJob?.cancel()
                    detectionJob = null
                } else {
                    startListening()
                }
            }
        }
    }

    private fun startListening() {
        if (detectionJob?.isActive == true) return
        android.util.Log.d(DEBUG_TAG, "startListening: registering background sensor collection")
        detectionJob = serviceScope.launch {
            // BUG FIX (found via manual on-device testing, 2026-11-18): user-reported "shake does
            // nothing while the app is closed" was real, confirmed via dumpsys sensorservice -
            // SENSOR_DELAY_NORMAL registers at ~5Hz (samplingPeriod=200000us), one reading every
            // 200ms. ShakeDetector's detection algorithm needs at least 2 threshold-crossings
            // within its rolling window (see that class's own doc), but a real hand-shake's full
            // back-and-forth cycle is typically 150-400ms - at 5Hz the sampler can easily land
            // entirely between the gesture's actual peaks and never observe a crossing at all,
            // even though the exact same physical shake is caught reliably by the foreground
            // listener's SENSOR_DELAY_GAME (~50Hz). SENSOR_DELAY_UI (~16Hz, samplingPeriod~60ms)
            // is still meaningfully lower-power than SENSOR_DELAY_GAME for a service that can run
            // indefinitely, while sampling often enough to actually catch a shake's peaks.
            shakeDetector.onShakeDetected(sensorDelay = SensorManager.SENSOR_DELAY_UI).collectLatest {
                val now = System.currentTimeMillis()
                if (now - lastBackgroundLaunchAtMs < BACKGROUND_LAUNCH_DEBOUNCE_MS) {
                    android.util.Log.d(DEBUG_TAG, "background onShakeDetected fired - debounced")
                    return@collectLatest
                }
                lastBackgroundLaunchAtMs = now
                android.util.Log.d(DEBUG_TAG, "background onShakeDetected fired")
                launchCaptureFromBackground()
            }
        }
    }

    // BUG FIX (2026-11-20, same root cause as the preferenceWatcherJob above): START_STICKY was
    // what let Android revive this service, with a null Intent and no toggle check, after killing
    // it for resource pressure - the one path that could make "shake in background" come back on
    // by itself. START_NOT_STICKY means a system-initiated kill simply stays stopped; the
    // legitimate "service should be running" cases (user turns the toggle on, app restarts after
    // reboot, app process is recreated after being swiped away) already explicitly call
    // startService/startForegroundService themselves - see HomeScreen.onBackgroundShakeToggle,
    // BootShakeReceiver, and MobAppAssignment.restartBackgroundShakeIfEnabled.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foregroundWatcherJob?.cancel()
        preferenceWatcherJob?.cancel()
        detectionJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // BUG FIX 2026-10-08 (second attempt, after live logcat on the real test device proved a
    // plain startActivity() call IS genuinely blocked here): logcat showed an explicit
    // "Background activity launch blocked! ... callingUidProcState: FOREGROUND_SERVICE ...
    // resultIfPiCreatorAllowsBal: BAL_BLOCK ... result code=102" - so despite being a real,
    // already-running foreground service, this specific device/Android version still refuses a
    // bare startActivity() call from it. A full-screen-intent notification was tried next and
    // also failed (confirmed by the user: never auto-presented, screen on OR locked - Android 14+
    // demotes full-screen intents to a plain tap-required notification for any app not
    // categorised as a calling/alarm app). This is the one remaining real auto-launch mechanism:
    // ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    // is Android's own explicit, newer opt-in for exactly this "a running foreground service wants
    // to launch its own Activity" case, sent immediately via PendingIntent.send() rather than
    // startActivity() directly (the mode flag only takes effect on the PendingIntent send path).
    // ROOT CAUSE FOUND 2026-11-18 (real-device testing on Android 17/API 37): the
    // ActivityOptions.setPendingIntentBackgroundActivityStartMode approach below used to work
    // (see its own still-accurate comment on what it does and why) but logcat now shows Android
    // evaluates the opt-in and still returns BAL_BLOCK overall - the platform has tightened BAL
    // enforcement since this was last verified. A full-screen intent notification is a SEPARATE
    // exemption that (per Android's own docs) is still honored for presenting over a locked/off
    // screen regardless of BAL state - this is now the primary launch mechanism. The direct
    // PendingIntent.send() is kept as a secondary attempt: harmless if it's blocked (silently a
    // no-op from this app's perspective, same as before), free if some device/future Android
    // version does still honor it.
    private fun launchCaptureFromBackground() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_LAUNCH_CAPTURE, true)
        }

        // ROOT-CAUSE FIX (2026-11-18, confirmed via live logcat on a real device, 5 consecutive
        // successful round-trips): neither a bare startActivity() nor PendingIntent.send() with
        // the ActivityOptions BAL opt-in gets past this device's BAL_BLOCK, and a full-screen-
        // intent notification posts correctly but this Samsung/OneUI build does not auto-present
        // it (known OneUI behavior gap, not fixable from this app's manifest/channel setup - every
        // official precondition is already met). SYSTEM_ALERT_WINDOW is the one remaining
        // mechanism Android still exempts from BAL - confirmed via logcat that merely HOLDING the
        // permission is enough (result: BAL_ALLOW_SAW_PERMISSION, MainActivity actually launched
        // and foregrounded, real system camera opened right after) - no overlay view needs to
        // exist at the moment of the call, only the permission grant itself. This is now the
        // primary and, once granted, SOLE launch path - the PendingIntent/notification fallback
        // below only runs when this permission isn't granted, so a user who has it set up never
        // sees the "tap to open" notification in addition to the camera actually opening.
        if (android.provider.Settings.canDrawOverlays(this)) {
            startActivity(intent)
            return
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            CAPTURE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        postCaptureAlertNotification(pendingIntent)

        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        } else {
            ActivityOptions.makeBasic()
        }
        try {
            pendingIntent.send(null, 0, null, null, null, null, options.toBundle())
        } catch (_: PendingIntent.CanceledException) {
            // The PendingIntent was cancelled between creation and send (e.g. the service was
            // torn down mid-shake) - nothing meaningful to recover here, and the next shake will
            // simply try again with a freshly created PendingIntent.
        }
    }

    // Requires USE_FULL_SCREEN_INTENT (manifest) AND this app's capture-alert channel to be
    // IMPORTANCE_HIGH (see MobAppAssignment.createBackgroundShakeNotificationChannel) - either
    // gap alone makes Android silently demote this to sitting in the notification shade instead
    // of auto-presenting, with no error or crash to indicate why. On API 34+, USE_FULL_SCREEN_INTENT
    // itself may also need the user's one-time approval in system settings for a regular app (not
    // a calling/alarm app) - NotificationManagerCompat.canUseFullScreenIntent() reports whether
    // that's currently granted; this still posts the notification either way (it is never
    // silently wrong to post one - at worst it is a regular, tap-to-open notification).
    private fun postCaptureAlertNotification(contentIntent: PendingIntent) {
        val canUseFsi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            androidx.core.app.NotificationManagerCompat.from(this).canUseFullScreenIntent()
        } else {
            true
        }
        android.util.Log.d(DEBUG_TAG, "postCaptureAlertNotification: canUseFullScreenIntent=$canUseFsi")

        val notification = NotificationCompat.Builder(this, CAPTURE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Shake detected")
            .setContentText("Tap to open the report camera.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(CAPTURE_NOTIFICATION_ID, notification)
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
        const val CAPTURE_CHANNEL_ID = "shake_detected_capture"
        private const val NOTIFICATION_ID = 42
        private const val CAPTURE_NOTIFICATION_ID = 43
        private const val CAPTURE_REQUEST_CODE = 44
        private const val DEBUG_TAG = "ShakeDebug"
        private const val BACKGROUND_LAUNCH_DEBOUNCE_MS = 5_000L
    }
}
