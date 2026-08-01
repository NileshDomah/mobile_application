package com.king.mob_app_assignment

import android.app.Application
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.util.ConnectivityObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobAppAssignment : Application() {

    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var reportRepository: ReportRepository

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        var wasOnline = connectivityObserver.isOnlineNow()
        appScope.launch {
            connectivityObserver.isOnline().collect { isOnline ->
                if (isOnline && !wasOnline) {
                    reportRepository.syncPendingReports()
                }
                wasOnline = isOnline
            }
        }
    }
}
