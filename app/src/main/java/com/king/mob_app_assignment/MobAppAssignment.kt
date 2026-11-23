package com.king.mob_app_assignment

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.king.mob_app_assignment.ai.RiskRefreshWorker
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.sensor.ShakeBackgroundService
import com.king.mob_app_assignment.sensor.startShakeService
import com.king.mob_app_assignment.util.AdminSupportNotifier
import com.king.mob_app_assignment.util.ConnectivityObserver
import com.king.mob_app_assignment.util.CrashReporter
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MobAppAssignment : Application(), Configuration.Provider {

    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var reportRepository: ReportRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var adminSupportNotifier: AdminSupportNotifier
    @Inject lateinit var shakePreferences: ShakePreferences

    // Lets ClassificationWorker (and any future Worker) receive its dependencies via Hilt's
    // @HiltWorker/@AssistedInject instead of manually looking them up - requires disabling
    // WorkManager's default androidx.startup initializer in the manifest (see
    // AndroidManifest.xml, the <provider tools:node="remove"> entry) so this Configuration is
    // the only one WorkManager ever sees.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize()
        createBackgroundShakeNotificationChannel()

        var wasOnline = connectivityObserver.isOnlineNow()
        appScope.launch {
            connectivityObserver.isOnline().collect { isOnline ->
                if (isOnline && !wasOnline) {
                    reportRepository.syncPendingReports()
                }
                wasOnline = isOnline
            }
        }

        // Ties crash reports to the signed-in Firebase uid only (never email/displayName)
        // so crashes can be correlated with a user's session without logging any PII.
        appScope.launch {
            authRepository.currentUser.collect { user ->
                CrashReporter.setUserId(user?.uid)
            }
        }

        enqueueRiskRefreshWork()
        adminSupportNotifier.start(appScope)
        restartBackgroundShakeIfEnabled()
    }

    // Runs on every fresh process creation - covers reboot (paired with BootShakeReceiver, which
    // handles the case where the process isn't created again until the user opens the app) and,
    // more importantly, the case a plain BOOT_COMPLETED receiver can't: the user swiping the app
    // away from Recents kills this foreground service along with the rest of the process, and
    // nothing else would ever tell it to come back - simply reopening the app (which recreates
    // this Application) is what should re-arm it, matching "shake still works once the app is
    // closed" rather than only "until the app is swiped away." startForegroundService is itself
    // idempotent-safe to call on an already-running service (it's just a redundant onStartCommand).
    private fun restartBackgroundShakeIfEnabled() {
        appScope.launch {
            if (shakePreferences.backgroundShakeEnabledFlow.first()) {
                startShakeService(this@MobAppAssignment)
            }
        }
    }

    // Predictive risk scoring is recomputed on a schedule, not per-report (unlike incident
    // clustering) - a single new report shouldn't trigger a full recompute of every area's risk
    // bucket. enqueueUniquePeriodicWork with KEEP means this is a no-op on every app start after
    // the first - WorkManager already has the periodic request registered and honors its own
    // schedule. Network-required (unlike clustering) since a run reads the full reports
    // collection, not one already-cached document.
    private fun enqueueRiskRefreshWork() {
        val request = PeriodicWorkRequestBuilder<RiskRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RiskRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun createBackgroundShakeNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ShakeBackgroundService.CHANNEL_ID,
            "Background shake listener",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent notification shown while Shake to Report listens for a shake in the background"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        // BUG FIX (2026-11-18): a full-screen intent is only eligible to auto-present (rather
        // than sit silently in the shade) if its OWN channel is IMPORTANCE_HIGH - the persistent
        // "listening" channel above is deliberately LOW (it shouldn't be intrusive just for
        // running), so a genuine shake-triggered capture alert needs a separate channel entirely.
        // See ShakeBackgroundService.CAPTURE_CHANNEL_ID/buildCaptureAlertNotification.
        val captureChannel = NotificationChannel(
            ShakeBackgroundService.CAPTURE_CHANNEL_ID,
            "Shake detected",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shown briefly when a shake is detected while the app is closed, to bring up the report camera"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(captureChannel)
    }
}
