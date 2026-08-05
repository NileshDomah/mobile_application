package com.king.mob_app_assignment.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.king.mob_app_assignment.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val CHANNEL_ID = "report_status_updates"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Report status updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifies you when one of your submitted reports changes status"
            }
            val manager = context.getSystemService(Context::class.java)
                ?.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun notifyStatusChanged(reportId: String, category: String, newStatus: String, resolutionNote: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val baseText = "Your $category report is now $newStatus"
        val contentText = if (resolutionNote.isNotBlank()) {
            val snippet = resolutionNote.take(60).let { if (resolutionNote.length > 60) "$it..." else it }
            "$baseText: $snippet"
        } else {
            baseText
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Report update")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(reportId.hashCode(), notification)
    }
}
