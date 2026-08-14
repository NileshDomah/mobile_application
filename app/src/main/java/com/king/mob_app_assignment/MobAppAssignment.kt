package com.king.mob_app_assignment

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.sensor.ShakeBackgroundService
import com.king.mob_app_assignment.util.ConnectivityObserver
import com.king.mob_app_assignment.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobAppAssignment : Application(), Configuration.Provider {

    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var reportRepository: ReportRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var workerFactory: HiltWorkerFactory

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
    }
}
