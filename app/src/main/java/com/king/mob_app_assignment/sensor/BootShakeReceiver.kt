package com.king.mob_app_assignment.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.king.mob_app_assignment.util.ShakePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts [ShakeBackgroundService] after a device reboot if the user had "shake in background"
 * turned on - a foreground Service (even START_STICKY) is not automatically relaunched across a
 * reboot, only across the service's own process dying while the device stays up. Without this,
 * "shake works when the app is closed" would silently stop being true the very next time the
 * phone restarts, with no obvious symptom pointing back at the cause.
 *
 * BroadcastReceivers can't take a normal constructor injection (the OS instantiates them via a
 * no-arg constructor), so this uses Hilt's field injection via @AndroidEntryPoint instead - the
 * same pattern services/activities use, just via onReceive() instead of onCreate().
 */
@AndroidEntryPoint
class BootShakeReceiver : BroadcastReceiver() {

    @Inject lateinit var shakePreferences: ShakePreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync() keeps the receiver (and process) alive long enough for this coroutine to
        // finish - onReceive() itself must return quickly, but reading the DataStore flow and
        // starting the service both involve a brief async hop.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = shakePreferences.backgroundShakeEnabledFlow.first()
                if (enabled) {
                    startShakeService(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Shared by [BootShakeReceiver] and anywhere else that needs to (re)start the background shake
 * listener - kept in one place so the startForegroundService/startService branching (required on
 * O+) is never duplicated or allowed to drift out of sync between call sites.
 */
fun startShakeService(context: Context) {
    val intent = Intent(context, ShakeBackgroundService::class.java)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    } catch (e: Exception) {
        // BUG FIX (2026-11-18, found via real-device testing - crashed on every app restart
        // after an install/update): android.app.ForegroundServiceStartNotAllowedException (API
        // 31+) is thrown when the calling context isn't itself foreground at the exact moment of
        // the call - both call sites here (boot, and MobAppAssignment's own restart-if-enabled
        // check on process start) run from exactly that kind of background context, and Android
        // does not guarantee this attempt will be allowed to succeed on every OS version/device.
        // Letting that propagate crashed the whole process immediately after every install -
        // a much worse outcome than silently not restarting the service, since the user can
        // simply reopen the app to get it running again (RootNavGraph's own flows already handle
        // "service not currently running" gracefully - this was never the only way it starts).
        com.king.mob_app_assignment.util.CrashReporter.recordException(e)
    }
}
