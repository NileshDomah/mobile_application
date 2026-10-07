package com.king.mob_app_assignment.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.util.AppPermission
import com.king.mob_app_assignment.util.PermissionManager

// Requested in this order every time the gate runs - Camera and Location are the two the app's
// core reporting flow genuinely can't work without, Notifications matters most for status-update
// pushes, so this is also roughly "most essential first" if the user starts declining partway
// through and the gate has to stop and show the "some permissions are still off" summary state.
private val GATE_PERMISSIONS = listOf(AppPermission.CAMERA, AppPermission.LOCATION, AppPermission.NOTIFICATIONS)

/**
 * Walks the user through granting Camera/Location/Notifications right at first entry into the
 * signed-in app, one at a time via PermissionExplainerScreen (reused as-is - same visual pattern
 * CaptureScreen already uses for its own on-demand camera/location prompts). Per explicit
 * request ("if entering for the first time prompt the user to allow all permission and if any is
 * denied on every entry prompt to allow"), this is NOT a one-time onboarding step the way
 * OnboardingScreen/PrivacyNoticeScreen are (see their own "shown once" DataStore flags) - this
 * gate has no completion flag of its own at all. It simply re-checks PermissionManager.isGranted
 * for all three permissions every time RootNavGraph would otherwise show MAIN_GRAPH, and renders
 * itself (blocking entry into the real app) for as long as any of them are still denied. Once
 * every one of the three is granted, [onAllResolved] fires and this composable is never shown
 * again until a permission gets revoked from system Settings and the app is reopened.
 *
 * "Resolved" deliberately means "asked", not "granted" - a user can tap Deny on all three and the
 * gate still needs to let them into the app (there is no permission the OS lets an app force a
 * user into granting), but the NEXT time they open the app it asks again for whichever ones are
 * still missing, per the explicit request. isPermanentlyDenied (the OS's own "don't ask again"
 * signal) routes that specific permission to an explainer pointing at system Settings instead of
 * re-showing a request dialog Android would silently no-op anyway.
 */
@Composable
fun StartupPermissionGate(onAllResolved: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var stepIndex by remember { mutableIntStateOf(0) }

    // Recomputed fresh each time this reaches a new stepIndex (not cached) - isGranted() reflects
    // whatever the system permission dialog or a prior loop through this gate already resolved,
    // so a permission granted earlier in the SAME pass through GATE_PERMISSIONS is correctly
    // skipped without a redundant prompt.
    fun advanceToNextUngranted(from: Int) {
        var next = from
        while (next < GATE_PERMISSIONS.size && PermissionManager.isGranted(context, GATE_PERMISSIONS[next])) {
            next++
        }
        stepIndex = next
    }

    LaunchedEffect(Unit) {
        advanceToNextUngranted(0)
    }

    if (stepIndex >= GATE_PERMISSIONS.size) {
        LaunchedEffect(Unit) { onAllResolved() }
        return
    }

    val currentPermission = GATE_PERMISSIONS[stepIndex]

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        advanceToNextUngranted(stepIndex + 1)
    }

    val (icon, titleRes, bodyRes) = when (currentPermission) {
        AppPermission.CAMERA -> Triple(Icons.Filled.CameraAlt, R.string.startup_permission_camera_title, R.string.startup_permission_camera_body)
        AppPermission.LOCATION -> Triple(Icons.Filled.LocationOn, R.string.startup_permission_location_title, R.string.startup_permission_location_body)
        AppPermission.NOTIFICATIONS -> Triple(Icons.Filled.Notifications, R.string.startup_permission_notifications_title, R.string.startup_permission_notifications_body)
    }

    val isPermanentlyDenied = activity != null && PermissionManager.isPermanentlyDenied(activity, currentPermission)

    if (isPermanentlyDenied) {
        PermissionExplainerScreen(
            icon = icon,
            title = stringResource(titleRes),
            body = stringResource(R.string.startup_permission_denied_body, currentPermission.label),
            continueButtonLabel = stringResource(R.string.startup_permission_open_settings),
            onContinue = {
                context.startActivity(PermissionManager.appSettingsIntent(context))
                // Optimistic: re-check once the user comes back rather than blocking on an
                // ActivityResult contract for Settings itself - onResume-driven recomposition
                // (see MainActivity's own lifecycle handling) will naturally re-run this gate the
                // next time RootNavGraph composes it, picking up whatever they changed.
                advanceToNextUngranted(stepIndex + 1)
            },
        )
    } else {
        PermissionExplainerScreen(
            icon = icon,
            title = stringResource(titleRes),
            body = stringResource(bodyRes),
            onContinue = {
                // Recorded right before actually launching the real system dialog - see
                // PermissionRequestHistory's own doc comment for why this is what lets
                // isPermanentlyDenied() tell "never asked" apart from "genuinely denied."
                com.king.mob_app_assignment.util.PermissionRequestHistory.markRequested(context, currentPermission)
                permissionLauncher.launch(currentPermission.manifestPermission)
            },
        )
    }
}
