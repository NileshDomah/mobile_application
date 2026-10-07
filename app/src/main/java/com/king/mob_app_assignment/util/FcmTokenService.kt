package com.king.mob_app_assignment.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.king.mob_app_assignment.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// Registered in AndroidManifest.xml. Android/FCM instantiates this itself outside Hilt's normal
// component graph, so @AndroidEntryPoint is required to get field injection here at all (plain
// constructor injection doesn't work for framework-instantiated components like this).
//
// Two responsibilities:
//  1. onNewToken() - captures this device's FCM registration token whenever it's issued or
//     rotates (first install, app data cleared, token refresh) and pushes it to
//     /users/{uid}.fcmToken via AuthRepository.updateFcmToken(), so the sendReportStatusPush
//     Cloud Function (functions/src/index.ts) knows where to deliver a push for this user.
//  2. onMessageReceived() - only matters for a DATA-only message arriving while the app is in
//     the foreground; a NOTIFICATION-payload message (which is what sendReportStatusPush sends)
//     is displayed by the OS automatically without this callback firing at all when the app is
//     backgrounded/killed - that's the whole point of using FCM over the pre-existing local-only
//     NotificationHelper (see PLACEHOLDERS_TODO.csv for that gap this feature closes).
@AndroidEntryPoint
class FcmTokenService : FirebaseMessagingService() {

    @Inject lateinit var authRepository: AuthRepository

    // This service has no lifecycle owner to scope a coroutine to (it's not a ViewModel/
    // Activity), and its callbacks are fire-and-forget from FCM's perspective - a
    // SupervisorJob-backed scope means a failure in one onNewToken() call can't cancel a
    // later, unrelated one.
    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            authRepository.updateFcmToken(token).onFailure { error ->
                Log.w(TAG, "Failed to persist refreshed FCM token", error)
            }
        }
    }

    // Notification-payload pushes (what the Cloud Function sends) are rendered by the OS
    // directly when the app is backgrounded or killed - this only fires for a foreground app,
    // where FCM hands the message to the app instead of drawing it itself. Nothing to do here
    // yet since sendReportStatusPush's payload is display-only (title/body), not a data payload
    // requiring custom handling; kept as an explicit override so this is a deliberate no-op,
    // not an oversight, and as the hook point if a future feature needs foreground-only handling.
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
    }

    companion object {
        private const val TAG = "FcmTokenService"
    }
}
