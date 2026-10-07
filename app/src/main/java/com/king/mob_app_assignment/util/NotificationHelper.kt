package com.king.mob_app_assignment.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.king.mob_app_assignment.MainActivity
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.reportReferenceCode
import com.king.mob_app_assignment.ui.nav.Destinations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shakePreferences: ShakePreferences,
) {

    companion object {
        private const val CHANNEL_ID = "report_status_updates"
        private const val ADMIN_CHANNEL_ID = "admin_events"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Report status updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Notifies you when one of your submitted reports changes status"
                },
            )
            manager?.createNotificationChannel(
                NotificationChannel(
                    ADMIN_CHANNEL_ID,
                    "Admin events",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Notifies admins of new citizen support messages while the app is running"
                },
            )
        }
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Deep-links straight to the relevant screen on tap, rather than just re-opening the app to
    // whatever screen it happened to be on last - EXTRA_TARGET_ROUTE is consumed once by
    // MainActivity the same way EXTRA_LAUNCH_CAPTURE already is for the shake-background-service
    // deep link, so both notification types and the shake service share one mechanism.
    private fun contentIntentFor(route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_TARGET_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun notifyStatusChanged(reportId: String, category: String, newStatus: String, resolutionNote: String = "") {
        if (!hasPostNotificationPermission()) return
        // A single-key DataStore read on a small file backing a Firestore listener callback (not
        // a hot loop) - fast enough that a blocking read here is unremarkable, and simpler than
        // threading a CoroutineScope through ReportRepositoryImpl just for this.
        if (!runBlocking { shakePreferences.notifyStatusUpdatesFlow.first() }) return

        val reference = reportReferenceCode(reportId)
        val baseText = "Your $category report is now $newStatus"
        val contentText = if (resolutionNote.isNotBlank()) {
            val snippet = resolutionNote.take(60).let { if (resolutionNote.length > 60) "$it..." else it }
            "$baseText: $snippet"
        } else {
            baseText
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Report update - $reference")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntentFor(Destinations.reportDetail(reportId)))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(reportId.hashCode(), notification)
    }

    // Sent when ClassificationWorker exhausts its retries without a usable result from either
    // the Gemma vision LLM or the ML Kit fallback (e.g. no network reachable at all, LM Studio
    // down AND ML Kit itself throwing). Distinct notification id space (reportId.hashCode() + 1)
    // so it can never collide with/replace a status-changed notification for the same report.
    fun notifyClassificationFailed(reportId: String) {
        if (!hasPostNotificationPermission()) return
        if (!runBlocking { shakePreferences.notifyAiClassificationFlow.first() }) return

        val reference = reportReferenceCode(reportId)
        val contentText = "We couldn't automatically detect the issue type - please open $reference and choose a category."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Manual input needed - $reference")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntentFor(Destinations.reportDetail(reportId)))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(reportId.hashCode() + 1, notification)
    }

    // The other half of "tell me whether the AI could classify this or not" - fired when
    // ClassificationWorker's review() call succeeds (AUTO_CATEGORIZED or CONFIRMED; FLAGGED also
    // counts as "the AI ran", just disagreeing with what the citizen picked, so it's included
    // too - only a genuine FAILED result goes to notifyClassificationFailed above instead).
    // Distinct id space (reportId.hashCode() + 2) so this can never collide with either the
    // status-changed or classification-failed notification for the same report.
    fun notifyClassificationSucceeded(reportId: String, category: String) {
        if (!hasPostNotificationPermission()) return
        if (!runBlocking { shakePreferences.notifyAiClassificationFlow.first() }) return

        val reference = reportReferenceCode(reportId)
        val contentText = "We identified this as $category. Tap to view $reference."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Issue detected - $reference")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntentFor(Destinations.reportDetail(reportId)))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(reportId.hashCode() + 2, notification)
    }

    // Fired locally on an admin's own device when their live support-inbox listener sees a new
    // OPEN message arrive while the app is running in the background - see
    // AdminSupportNotifier.kt for the "only messages newer than when this collector started"
    // guard that keeps this from replaying the whole existing inbox as notifications on login.
    // Purely local, same architecture as every other notification in this app (no Cloud Function/
    // FCM push - see PLACEHOLDERS_TODO.csv) - it only fires while this admin's own device has the
    // app process alive, not truly in the background/killed state.
    fun notifyNewSupportMessage(messageId: String, userDisplayName: String, message: String) {
        if (!hasPostNotificationPermission()) return

        val fromName = userDisplayName.ifBlank { "A citizen" }
        val snippet = message.take(80).let { if (message.length > 80) "$it..." else it }

        val notification = NotificationCompat.Builder(context, ADMIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("New support message from $fromName")
            .setContentText(snippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText(snippet))
            .setContentIntent(contentIntentFor(Destinations.ADMIN_SUPPORT_MESSAGES))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(messageId.hashCode(), notification)
    }
}
