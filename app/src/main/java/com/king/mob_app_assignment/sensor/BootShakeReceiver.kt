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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
}
