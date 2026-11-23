package com.king.mob_app_assignment.ui.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.king.mob_app_assignment.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.sensor.ShakeBackgroundService
import com.king.mob_app_assignment.sensor.startShakeService
import com.king.mob_app_assignment.ui.components.InfoCard
import com.king.mob_app_assignment.ui.components.InfoCardSeverity
import com.king.mob_app_assignment.ui.theme.Spacing
import com.king.mob_app_assignment.ui.theme.translatedStringResource
import com.king.mob_app_assignment.util.AppPermission
import com.king.mob_app_assignment.util.PermissionManager

/**
 * Landing screen for the shake-to-report gesture - a hero illustration (hand holding a phone,
 * shake motion lines, a light city skyline) plus two entry points into reporting: "Shake Now"
 * (jumps straight into the same viaShake=true capture flow the real accelerometer gesture
 * reaches - see MainActivity's shakeDetector collector) and "New Report" (the form-first flow,
 * see CaptureViewModel.startFormFirstFlow). Deliberately just this - no stats/recent-activity
 * preview - matching the reference mockup exactly; ProfileScreen already shows the same impact
 * stats independently, and MyReportsScreen already shows the full report history, so nothing
 * here was otherwise unreachable. Still depends on HomeViewModel though - not for anything it
 * renders, but because its init block starts reportRepository.startStatusChangeListener(), which
 * is what makes report-status-change push notifications work at all; losing that would have been
 * a silent regression unrelated to this screen's own visual redesign.
 */
@Composable
fun HomeScreen(
    isAdmin: Boolean = false,
    onNavigateToCapture: (viaShake: Boolean) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val backgroundShakeEnabled by viewModel.backgroundShakeEnabled.collectAsState()
    val context = LocalContext.current

    // Same permission-gated enable/disable flow as SettingsScreen's own "Shake in background"
    // switch (both read/write the same ShakePreferences flag, so they always agree with each
    // other) - surfaced here too per explicit request that this toggle be reachable without
    // digging into Settings first.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundShakeEnabled(true)
            startShakeService(context)
        }
    }
    fun onBackgroundShakeToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.setBackgroundShakeEnabled(false)
            context.stopService(Intent(context, ShakeBackgroundService::class.java))
            return
        }
        // BUG FIX (2026-11-18, real-device testing): confirmed via live logcat that neither a
        // bare startActivity() nor the ActivityOptions BAL opt-in nor a full-screen-intent
        // notification (this Samsung/OneUI build doesn't auto-present FSI for a non-telecom app
        // even with every official precondition met - canUseFullScreenIntent() true, HIGH
        // channel) actually opens the camera from the background service on this device/Android
        // version. SYSTEM_ALERT_WINDOW ("draw over other apps") is the one remaining mechanism
        // Android still exempts from BAL - an app holding it has a real visible overlay window,
        // satisfying the callingUidHasNonAppVisibleWindow check the BAL decision logcat already
        // showed mattering. Requested here (once, when the user turns this feature ON) rather
        // than at app startup, since it's only needed for this specific feature, not core to the
        // app the way Camera/Location are.
        if (!android.provider.Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (PermissionManager.isGranted(context, AppPermission.NOTIFICATIONS)) {
            viewModel.setBackgroundShakeEnabled(true)
            startShakeService(context)
        } else {
            notificationPermissionLauncher.launch(AppPermission.NOTIFICATIONS.manifestPermission)
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            // BUG FIX (found via manual testing, 2026-11-18): this Column had no scroll modifier,
            // so on a real device its content (hero illustration + buttons + "Shake in
            // background" card) was taller than the space actually available once the status bar
            // and bottom nav bar insets were subtracted - Compose's fixed-height layout squeezed
            // the last card down to a few pixels tall instead of letting it overflow scrollably,
            // clipping its description text to an unreadable sliver every time Home loaded.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Text(
                text = translatedStringResource(R.string.home_hero_title_line1),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                text = translatedStringResource(R.string.home_hero_title_line2),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = translatedStringResource(R.string.home_shake_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )

            // Shown in both themes now - see ShakeHeroIllustration's own doc comment for why
            // this used to be light-theme-only and what changed (user-reported: "if the phone is
            // on dark mode the image on home disappear that not good" - skipping the whole
            // illustration left an abrupt empty gap in the layout, which read as broken, not as
            // a deliberate design choice).
            ShakeHeroIllustration(
                dimmed = isSystemInDarkTheme(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .padding(top = Spacing.md),
            )

            // CHANGED 2026-09 per explicit request ("in home add the same function to report as
            // normal user for admin") - an admin was previously unable to file a report at all
            // from Home (this whole block was gated behind !isAdmin, on the earlier reasoning
            // that an admin only manages citizens' reports rather than filing their own). An
            // admin account can still shake/tap through the exact same capture flow a citizen
            // does - nothing else about the admin experience (bottom nav still drops MY_REPORTS
            // for admins, AdminReportsScreen/AdminDashboardScreen are unchanged) is affected by
            // this - it only restores the ability to report an issue personally on top of the
            // existing admin tabs.
            // Deliberately NOT a button - shaking the phone is the only way to trigger this
            // path (see MainActivity's shakeDetector collector); this just labels that
            // affordance in the same visual slot/style a "Shake Now" button would occupy,
            // without actually being tappable, so it can't be mistaken for a second way to
            // trigger capture alongside the real gesture.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg)
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = translatedStringResource(R.string.home_shake_now_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            OutlinedButton(
                onClick = { onNavigateToCapture(false) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = translatedStringResource(R.string.home_new_report_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // Same "Shake in background" toggle as Settings, surfaced here too so it's visible
            // without navigating away from Home first (explicit request: "make it more
            // visible/accessible").
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translatedStringResource(R.string.settings_shake_in_background_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = translatedStringResource(R.string.settings_shake_in_background_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(
                        checked = backgroundShakeEnabled,
                        onCheckedChange = ::onBackgroundShakeToggle,
                    )
                }
            }

            if (uiState.pendingSyncCount > 0) {
                val plural = if (uiState.pendingSyncCount == 1) "report" else "reports"
                InfoCard(
                    title = "Waiting to sync",
                    message = "${uiState.pendingSyncCount} $plural saved on this device only - " +
                        "they'll upload automatically once you're back online.",
                    icon = Icons.Filled.CloudOff,
                    severity = InfoCardSeverity.Warning,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md),
                )
            }
        }
    }
}

/**
 * The hand-holding-a-phone hero artwork (res/drawable-nodpi/home_shake_hero.webp) rendered so it
 * reads as part of the page rather than a pasted-in picture: no card, border, or background box
 * around it (unlike a typical "image in a frame" treatment) - its own pale background is close
 * enough to the light theme's own background (CivicNeutral99) that the two blend, and a soft
 * alpha fade (drawWithContent + a vertical gradient mask) dissolves its actual rectangular edges
 * into transparency on all four sides, so there's no visible seam where the artwork ends and the
 * screen's own background begins.
 *
 * [dimmed] (pass true in dark theme): this used to mean "don't show this at all" - the artwork's
 * pale background clashes against the dark theme's near-black background, so it was skipped
 * entirely there. That left an abrupt empty gap in the Home layout instead, which read as broken
 * rather than as a deliberate choice (user-reported: "if the phone is on dark mode the image on
 * home disappear that not good"). Now it's still shown in dark theme, just with a wider edge fade
 * and a semi-transparent dark scrim drawn over it (BlendMode.SrcAtop, so it only darkens pixels
 * the artwork actually painted, never the transparent fade itself) - the pale background reads as
 * a dim, receded illustration instead of a bright clashing rectangle.
 */
@Composable
private fun ShakeHeroIllustration(dimmed: Boolean, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.home_shake_hero),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (dimmed) {
                    drawRect(color = Color.Black.copy(alpha = 0.45f), blendMode = BlendMode.SrcAtop)
                }
                // Fades all four edges to transparent by drawing a black-to-transparent gradient
                // with BlendMode.DstIn (keep destination alpha only where the mask is opaque) -
                // cheaper than four separate linear gradients, and avoids double-darkening the
                // corners the way stacking two full-bleed linear gradients would. Dark theme uses
                // a noticeably wider fade (0.32 vs 0.22) so more of the pale background dissolves
                // into the surrounding near-black rather than staying a visible pale rectangle.
                val fade = size.minDimension * if (dimmed) 0.32f else 0.22f
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        (fade / size.height) to Color.Black,
                        1f - (fade / size.height) to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        (fade / size.width) to Color.Black,
                        1f - (fade / size.width) to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}
