package com.king.mob_app_assignment

import android.app.Application
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.util.ConnectivityObserver
import com.king.mob_app_assignment.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobAppAssignment : Application() {

    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var reportRepository: ReportRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize()

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
}
