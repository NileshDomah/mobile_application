package com.king.mob_app_assignment

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.domain.model.UserRole
import com.king.mob_app_assignment.sensor.ShakeDetector
import com.king.mob_app_assignment.ui.admin.AdminDashboardScreen
import com.king.mob_app_assignment.ui.admin.AdminIncidentsScreen
import com.king.mob_app_assignment.ui.admin.AdminReportsScreen
import com.king.mob_app_assignment.ui.admin.AdminRiskAreasScreen
import com.king.mob_app_assignment.ui.admin.AdminSupportMessagesScreen
import com.king.mob_app_assignment.ui.admin.IncidentDetailScreen
import com.king.mob_app_assignment.ui.admin.RiskAreaDetailScreen
import com.king.mob_app_assignment.ui.auth.ForgotCredentialsScreen
import com.king.mob_app_assignment.ui.auth.LoginScreen
import com.king.mob_app_assignment.ui.auth.RegisterScreen
import com.king.mob_app_assignment.ui.capture.CaptureScreen
import com.king.mob_app_assignment.ui.chat.ChatScreen
import com.king.mob_app_assignment.ui.home.HomeScreen
import com.king.mob_app_assignment.ui.nav.Destinations
import com.king.mob_app_assignment.ui.nav.PendingReportsBadgeViewModel
import com.king.mob_app_assignment.ui.onboarding.OnboardingScreen
import com.king.mob_app_assignment.ui.permissions.PermissionDashboardScreen
import com.king.mob_app_assignment.ui.permissions.PermissionDashboardViewModel
import com.king.mob_app_assignment.ui.profile.ProfileScreen
import com.king.mob_app_assignment.ui.reports.MyReportsScreen
import com.king.mob_app_assignment.ui.reports.ReportDetailScreen
import com.king.mob_app_assignment.ui.settings.SettingsScreen
import com.king.mob_app_assignment.ui.support.ContactSupportScreen
import com.king.mob_app_assignment.ui.theme.AppTheme
import com.king.mob_app_assignment.util.OnboardingPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var shakeDetector: ShakeDetector

    @Inject lateinit var onboardingPreferences: OnboardingPreferences

    @Inject lateinit var privacyNoticePreferences: com.king.mob_app_assignment.util.PrivacyNoticePreferences

    @Inject lateinit var translationPreferences: com.king.mob_app_assignment.util.TranslationPreferences

    @Inject lateinit var appLockPreferences: com.king.mob_app_assignment.util.AppLockPreferences

    @Inject lateinit var appPinStore: com.king.mob_app_assignment.util.AppPinStore

    // Set when launched from ShakeBackgroundService after a shake fired while the app was
    // closed/backgrounded - consumed once by the nav graph below to jump straight to Capture.
    //
    // BUG FIX (2026-11-20, "shake while locked does nothing"): this was a plain `var`, not
    // `mutableStateOf` like pendingTargetRoute right below it. onNewIntent landing on an
    // already-composed, already-stopped MainActivity instance (the normal case for a repeat
    // shake, since ShakeBackgroundService's startActivity() reuses the existing task) wrote this
    // field, but Compose has no way to observe a plain field write - the
    // LaunchedEffect(pendingCaptureLaunch, currentUser) below never saw its key change and never
    // re-ran, so the effect's "navigate to capture" log line never even appeared. Moving it to
    // mutableStateOf makes onNewIntent's write trigger recomposition exactly like
    // pendingTargetRoute already does.
    private var pendingCaptureLaunch by mutableStateOf(false)

    // Set when launched by tapping a notification (see NotificationHelper.contentIntentFor) -
    // holds a raw route string (a fixed Destinations constant, or "report_detail/{id}" with a
    // real id substituted in) to navigate to once, consumed the same way pendingCaptureLaunch is.
    // A mutableStateOf (not a plain var) so onNewIntent's assignment recomposes the LaunchedEffect
    // below even when the Activity is not recreated - a plain var wouldn't trigger recomposition
    // since Compose has no way to observe an ordinary field write.
    private var pendingTargetRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Safe to call on every onCreate (including Activity recreation after the camera trip
        // that used to re-trigger the PIN screen) - only the first call in this process actually
        // registers the app-wide backgrounding observer. See AppLockSessionState's own doc.
        com.king.mob_app_assignment.util.AppLockSessionState.ensureObserverRegistered()
        pendingCaptureLaunch = intent?.getBooleanExtra(EXTRA_LAUNCH_CAPTURE, false) == true
        pendingTargetRoute = intent?.getStringExtra(EXTRA_TARGET_ROUTE)
        if (pendingCaptureLaunch) {
            allowShowingOverLockScreenForShakeCapture()
            com.king.mob_app_assignment.util.AppLockSessionState.markShakeCaptureStarted()
        }

        setContent {
            val activeMlKitLanguageTag by translationPreferences.activeMlKitLanguageTagFlow
                .collectAsState(initial = "")

            androidx.compose.runtime.CompositionLocalProvider(
                com.king.mob_app_assignment.ui.theme.LocalActiveMlKitLanguageTag provides activeMlKitLanguageTag,
            ) {
            AppTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val hapticFeedback = LocalHapticFeedback.current

                    // BUG FIX 2026-10-08 ("in the app when I shake it still open twice" - reported
                    // AFTER the background/foreground cross-talk fixes above, so this is a PURE
                    // foreground-only double-fire, nothing to do with ShakeBackgroundService.
                    // launchSingleTop (below) only dedupes navigate() calls once Capture is
                    // ALREADY the current back-stack entry - it does nothing while navigation to
                    // Capture is still in flight, or while some other screen is still on top. A
                    // single vigorous/extended shake gesture can cross ShakeDetector's own
                    // threshold twice with a gap wider than its per-collector DEBOUNCE_MS, so
                    // collectLatest can legitimately emit twice for what the user experiences as
                    // one shake. This remember{} tracks the wall-clock time of the last ACCEPTED
                    // foreground navigation and ignores any further shake within the same short
                    // window, regardless of ShakeForegroundState's own (differently-scoped)
                    // cross-talk grace period.
                    var lastForegroundShakeNavigateAtMs by remember { mutableStateOf(0L) }

                    // LifecycleStartEffect (re)starts collection on ON_START and cancels it on
                    // ON_STOP, which registers/unregisters the SensorManager listener inside
                    // ShakeDetector's callbackFlow via awaitClose - so the listener is bound to
                    // onResume/onPause-equivalent lifecycle states and never leaks.
                    //
                    // ShakeForegroundState.setForeground bookends this exact same ON_START/ON_STOP
                    // window - see that object's own doc comment for the "shake opens the camera
                    // twice" bug this fixes: while this foreground listener is active,
                    // ShakeBackgroundService should not ALSO react to the same physical shake.
                    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
                        com.king.mob_app_assignment.sensor.ShakeForegroundState.setForeground(true)
                        val job = lifecycleScope.launch {
                            shakeDetector.onShakeDetected().collectLatest {
                                android.util.Log.d("ShakeDebug", "foreground onShakeDetected fired")
                                // ShakeBackgroundService now fully stops its own sensor
                                // registration while ShakeForegroundState.isForeground is true (see
                                // that service's own class doc for the 2026-10-08 root-cause fix),
                                // so there is no longer a background-trigger-then-foreground-
                                // reacts race to guard against here - only this debounce, against
                                // a single extended shake gesture re-crossing threshold twice.
                                val now = System.currentTimeMillis()
                                if (now - lastForegroundShakeNavigateAtMs < FOREGROUND_SHAKE_NAVIGATE_DEBOUNCE_MS) {
                                    android.util.Log.d("ShakeDebug", "foreground shake debounced")
                                    return@collectLatest
                                }
                                lastForegroundShakeNavigateAtMs = now
                                android.util.Log.d("ShakeDebug", "foreground navigating to capture")
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate(Destinations.capture(viaShake = true)) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        onStopOrDispose {
                            com.king.mob_app_assignment.sensor.ShakeForegroundState.setForeground(false)
                            job.cancel()
                        }
                    }

                    // currentUser/hasKnownAuthState: collectAsState(initial = null) has no way to
                    // distinguish "the flow just hasn't emitted yet" from "it emitted null" - both
                    // look identical (currentUser == null) on the very first composition. That
                    // ambiguity used to make RootNavGraph's one-time startGraph decision below
                    // ALWAYS see isLoggedIn == false on a cold start, even when Firebase Auth
                    // actually has a valid persisted session the real AuthStateListener just
                    // hadn't reported back yet - sending an already-logged-in user to the login
                    // screen every single time the app was reopened. hasKnownAuthState flips true
                    // only once a real emission has arrived (null or non-null), and RootNavGraph
                    // now waits for that before committing to a start destination.
                    var hasKnownAuthState by remember { mutableStateOf(false) }
                    val currentUserFlow = remember {
                        authRepository.currentUser.onEach { hasKnownAuthState = true }
                    }
                    val currentUser by currentUserFlow.collectAsState(initial = null)
                    val hasCompletedOnboarding by onboardingPreferences.hasCompletedOnboardingFlow
                        .collectAsState(initial = null)
                    val hasAcknowledgedPrivacyNotice by privacyNoticePreferences.hasAcknowledgedFlow
                        .collectAsState(initial = null)

                    // BUG FIX 2026-10-08 ("camera double open again"): this is a SECOND,
                    // independent path to the exact same navigate() call the foreground sensor
                    // collector above already debounces (lastForegroundShakeNavigateAtMs) - this
                    // one fires from onNewIntent (see MainActivity's own override) when
                    // ShakeBackgroundService's startActivity() lands on an already-running
                    // MainActivity instance rather than creating a fresh one. If the background
                    // service's isCurrentlyForeground() check has any race against the live
                    // foreground collector detecting the same physical shake, BOTH paths could
                    // navigate to Capture independently - the foreground debounce alone couldn't
                    // catch this because it only guarded its own collector's call site, not this
                    // one. Sharing the same debounce timestamp across both makes the protection
                    // apply regardless of which path a given shake happens to take.
                    androidx.compose.runtime.LaunchedEffect(pendingCaptureLaunch, currentUser) {
                        if (pendingCaptureLaunch && currentUser != null) {
                            android.util.Log.d("ShakeDebug", "pendingCaptureLaunch effect firing")
                            pendingCaptureLaunch = false
                            val now = System.currentTimeMillis()
                            if (now - lastForegroundShakeNavigateAtMs >= FOREGROUND_SHAKE_NAVIGATE_DEBOUNCE_MS) {
                                lastForegroundShakeNavigateAtMs = now
                                android.util.Log.d("ShakeDebug", "pendingCaptureLaunch navigating to capture")
                                navController.navigate(Destinations.capture(viaShake = true)) { launchSingleTop = true }
                            } else {
                                android.util.Log.d("ShakeDebug", "pendingCaptureLaunch debounced")
                            }
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(pendingTargetRoute, currentUser) {
                        val route = pendingTargetRoute
                        if (route != null && currentUser != null) {
                            pendingTargetRoute = null
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }

                    val hasCompletedAppLockSetup by appLockPreferences.hasCompletedSetupFlow
                        .collectAsState(initial = null)

                    RootNavGraph(
                        navController = navController,
                        isLoggedIn = if (hasKnownAuthState) currentUser != null else null,
                        isAdmin = currentUser?.role == UserRole.ADMIN,
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        hasAcknowledgedPrivacyNotice = hasAcknowledgedPrivacyNotice,
                        // BUG FIX (found via emulator testing): hasCompletedAppLockSetup lives in
                        // plain DataStore prefs, but the PIN itself lives in Keystore-backed
                        // EncryptedSharedPreferences (see AppPinStore) - these can legitimately
                        // desync, e.g. Android auto-backup/restore brings DataStore's "setup done"
                        // flag back on a new device/reinstall, but the Keystore key behind the PIN
                        // hash never survives that restore, leaving AppPinStore empty. Without this
                        // check, that combination permanently locked a genuinely-signed-in user out
                        // on AppLockScreen (verifyPin() can never succeed with no hash stored, and
                        // "Sign out instead" was the only escape). Treat "setup done" as false
                        // whenever no PIN actually exists, so this self-heals back into
                        // SetupAppLockScreen instead of a dead end.
                        hasCompletedAppLockSetup = hasCompletedAppLockSetup?.let { it && appPinStore.isPinSet() },
                        onOnboardingFinished = {
                            lifecycleScope.launch { onboardingPreferences.setOnboardingCompleted() }
                        },
                        onPrivacyNoticeAcknowledged = {
                            lifecycleScope.launch { privacyNoticePreferences.setAcknowledged() }
                        },
                        onSignOut = {
                            authRepository.logout()
                            lifecycleScope.launch { appLockPreferences.resetSetupCompleted() }
                        },
                    )
                }
            }
            }
        }
    }

    // A tapped notification's PendingIntent can deliver here instead of a fresh onCreate() when
    // this Activity is already running (standard launch mode, FLAG_ACTIVITY_CLEAR_TOP resolves
    // to onNewIntent rather than recreation) - without this override, tapping a notification
    // while the app is already open in the background would silently do nothing.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_LAUNCH_CAPTURE, false)) {
            pendingCaptureLaunch = true
            allowShowingOverLockScreenForShakeCapture()
            com.king.mob_app_assignment.util.AppLockSessionState.markShakeCaptureStarted()
        }
        intent.getStringExtra(EXTRA_TARGET_ROUTE)?.let { pendingTargetRoute = it }
    }

    // Shake-to-report is meant to work even while the phone is locked (see ShakeBackgroundService)
    // - without this, an Activity started from a background Service while the device is locked
    // just queues silently behind the lock screen instead of appearing, which looked exactly like
    // "shake didn't do anything" to a user who couldn't see anything happen until they separately
    // unlocked and reopened the app themselves.
    //
    // Deliberately scoped to ONLY the shake-capture launch path (pendingCaptureLaunch), never
    // applied unconditionally for a normal app open - the system camera intent that fires next
    // (see CaptureScreen's viaShake auto-launch) is itself allowed to run over the lock screen
    // (the same mechanism as a lock-screen camera shortcut), but nothing else in this app should
    // ever bypass the user's own PIN/biometric lock. CaptureViewModel's own submit/save path
    // requires the device to be unlocked again before it can complete (see requireDeviceUnlock in
    // CaptureScreen) - this only grants "can appear on top of the lock screen," not "can act while
    // still locked."
    private fun allowShowingOverLockScreenForShakeCapture() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    companion object {
        const val EXTRA_LAUNCH_CAPTURE = "extra_launch_capture"

        // See the foreground shake collector's own BUG FIX comment above (2026-10-08, "shake
        // still opens camera twice", a pure foreground-only double-fire distinct from the
        // background/foreground cross-talk ShakeForegroundState already guards against). Must
        // comfortably exceed ShakeDetector's own per-collector DEBOUNCE_MS (1500ms), since this
        // guards against collectLatest legitimately emitting twice for one extended gesture.
        private const val FOREGROUND_SHAKE_NAVIGATE_DEBOUNCE_MS = 2000L

        // Route string a tapped notification wants to land on - see NotificationHelper's
        // contentIntentFor(). A raw string rather than a typed sealed class since it's just
        // handed straight to navController.navigate(route) unchanged; any Destinations route
        // constant (or a Destinations.reportDetail(id)-style formatted route) is valid here.
        const val EXTRA_TARGET_ROUTE = "extra_target_route"
    }
}

@Composable
private fun RootNavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean?,
    isAdmin: Boolean,
    hasCompletedOnboarding: Boolean?,
    hasAcknowledgedPrivacyNotice: Boolean?,
    hasCompletedAppLockSetup: Boolean?,
    onOnboardingFinished: () -> Unit,
    onPrivacyNoticeAcknowledged: () -> Unit,
    onSignOut: () -> Unit,
) {
    // isLoggedIn is null only until the real Firebase AuthStateListener has delivered its first
    // callback (see MainActivity's hasKnownAuthState) - NOT the same thing as "known signed out".
    // This one MUST be waited on rather than defaulted like hasCompletedOnboarding/
    // hasAcknowledgedPrivacyNotice below: collectAsState(initial = null) starts every cold launch
    // at null before that first callback arrives, and if this composable had defaulted a null
    // isLoggedIn to "signed out" (the same "treat null as done-loading" convention used for
    // onboarding/privacy-notice), an already-signed-in returning user would get bounced to the
    // login screen on literally every app reopen, even with a perfectly valid persisted Firebase
    // session - this was a real, reported bug ("app keeps asking to log in every time"), not a
    // hypothetical. So the NavHost below simply isn't composed at all until isLoggedIn resolves.
    //
    // hasCompletedOnboarding is null until the first DataStore emission arrives; treat that
    // briefly as "already onboarded" so returning users aren't flashed the walkthrough while
    // the flag loads - a fresh install still gets it once collectAsState settles to false.
    //
    // Deliberately NOT recalculated on every recomposition: NavHost resets its back stack to a
    // new start destination whenever startDestination changes out from under it, so if this were
    // a plain `val` recomputed from the live isLoggedIn/hasCompletedOnboarding flows, the instant
    // Firebase Auth's own auth-state listener notifies (which happens the moment
    // createUserWithEmailAndPassword() succeeds, well before AuthRepositoryImpl.register()'s own
    // Firestore transaction has run) this would rebuild the graph and tear down AUTH_GRAPH's back
    // stack - including RegisterScreen's still-in-flight ViewModel - out from under the coroutine
    // still writing the user's /users, /usernames, /emails, /loginLookup documents, silently
    // cancelling it before any of those writes land (a cancelled coroutine doesn't surface as a
    // caught exception the way a real failure would, so this produced no visible error - just a
    // registered Firebase Auth account with no Firestore documents at all). Snapshotting the
    // decision once on first composition means only the explicit navigate() calls in
    // onRegisterSuccess/onLoginSuccess/onLoggedOut ever move the app between graphs after that -
    // exactly the ones that already wait for the real repository call to finish first.
    // hasAcknowledgedPrivacyNotice follows the exact same "null means still loading, treat as
    // already-done so a returning user isn't flashed anything" convention as
    // hasCompletedOnboarding above - the difference is this one is checked SEPARATELY from
    // onboarding (a user who completed onboarding before this screen existed still needs to see
    // it once), not folded into the same boolean.
    if (isLoggedIn == null) return

    val startGraph = remember {
        when {
            hasCompletedOnboarding == false -> Destinations.ONBOARDING_GRAPH
            hasAcknowledgedPrivacyNotice == false -> Destinations.PRIVACY_NOTICE_GRAPH
            isLoggedIn -> Destinations.MAIN_GRAPH
            else -> Destinations.AUTH_GRAPH
        }
    }

    // Per explicit request ("if entering for the first time prompt the user to allow all
    // permission and if any is denied on every entry prompt to allow") - re-checked on every
    // entry into the signed-in app, not tracked with a "shown once" DataStore flag the way
    // onboarding/privacy-notice are (see StartupPermissionGate's own doc comment for why). Kept
    // OUTSIDE the NavHost/startGraph decision above rather than as a fourth graph choice: it must
    // re-run every time MAIN_GRAPH becomes the active destination (including navigating back into
    // it after AUTH_GRAPH on a fresh sign-in), not just once at cold start the way startGraph's
    // remember{} is - so it's driven by the live current back-stack entry instead.
    val currentDestination by navController.currentBackStackEntryAsState()
    var permissionGateResolved by remember { mutableStateOf(false) }
    val isInMainGraph = currentDestination?.destination?.hierarchy?.any { it.route == Destinations.MAIN_GRAPH } == true

    // App-lock (PIN, required, biometric optional on top - per explicit request replacing
    // "email/password every time" with a lock-screen model) has two states this composable
    // reacts to, on top of hasCompletedAppLockSetup (persisted - see AppLockPreferences).
    //
    // BUG FIX 2026-10-08 ("if I go back from the camera it asks for PIN every time") + FEATURE
    // (explicit request: "I want the pin to have like sessions - if the app wasn't closed don't
    // ask, or if the app has been in background for too long then ask"): this used to be
    // `remember { mutableStateOf(false) }` directly in this composable - Activity-instance-scoped,
    // not process-scoped as its old comment intended. Launching the system camera backgrounds
    // MainActivity behind a separate, memory-heavy activity; Android frequently reclaims a
    // backgrounded Activity under that memory pressure, so returning from the camera often
    // recreated MainActivity from scratch, wiping this remember{} back to false and re-triggering
    // the PIN screen even though the user never really left. It now reads from
    // AppLockSessionState, a process-wide singleton (same pattern as ShakeForegroundState) that
    // survives Activity recreation, and which itself implements the timed-session behaviour via a
    // ProcessLifecycleOwner observer: a brief camera round-trip or app-switch leaves it unlocked,
    // but being backgrounded past its own timeout clears it on the next resume - see its own doc
    // comment for the exact mechanism. The mutableStateOf mirror below exists only so Compose can
    // observe a plain AtomicBoolean; it is re-synced from the singleton on every recomposition of
    // this composable (which already happens on every auth/nav state change relevant here), and
    // every write goes through both so they never drift apart.
    var isAppUnlockedThisSession by remember { mutableStateOf(com.king.mob_app_assignment.util.AppLockSessionState.isUnlocked()) }
    isAppUnlockedThisSession = com.king.mob_app_assignment.util.AppLockSessionState.isUnlocked()
    fun markAppUnlocked() {
        com.king.mob_app_assignment.util.AppLockSessionState.markUnlocked()
        isAppUnlockedThisSession = true
    }
    // FEATURE (explicit user request, 2026-11-18): a background shake used to get silently
    // swallowed whenever the app-lock session had timed out - AppLockScreen's overlay rendered on
    // top of the capture navigation that had already happened underneath it, so the user saw the
    // PIN screen flash and then nothing (the pending capture was already consumed with no visible
    // camera launch). Shake-triggered capture now bypasses this overlay the same way it already
    // bypasses the system keyguard (see allowShowingOverLockScreenForShakeCapture) - the user sees
    // the camera immediately, and the app's own PIN is asked for afterward instead, once the photo
    // is actually captured (see CaptureScreen's RequireDeviceUnlockOncePhotoCaptured, which already
    // does the same deferred-unlock timing for the system keyguard).
    val isShakeCaptureInProgress = com.king.mob_app_assignment.util.AppLockSessionState.isShakeCaptureInProgress()
    val needsAppLockSetup = isLoggedIn && hasCompletedAppLockSetup == false
    val needsAppLockUnlock = isLoggedIn && hasCompletedAppLockSetup == true && !isAppUnlockedThisSession && !isShakeCaptureInProgress

    // BUG FIX 2026-10-01: "sign out instead" on the PIN lock screen bypassed login entirely,
    // landing back in whatever MAIN_GRAPH screen was already on the back stack underneath it.
    // onSignOut (passed in from MainActivity) correctly clears Firebase's session and the
    // app-lock-setup-completed flag, but AppLockScreen is only ever an overlay drawn on top of
    // the NavHost in the Box below (it is NOT itself a nav destination) - clearing auth state
    // just makes needsAppLockUnlock false again next recomposition, which removes the overlay and
    // reveals the MAIN_GRAPH screen that was sitting underneath the whole time, since nothing
    // ever told navController to actually pop back to AUTH_GRAPH. ProfileScreen's own sign-out
    // (see mainGraph's PROFILE destination below) already does this navigate+popUpTo correctly;
    // this wrapper gives every onSignOut call site in this composable the same real navigation,
    // and also resets the app-lock session so a later sign-in doesn't inherit a stale "already
    // unlocked" flag from the session that just ended.
    val onSignOutWithNavigation: () -> Unit = {
        onSignOut()
        com.king.mob_app_assignment.util.AppLockSessionState.reset()
        isAppUnlockedThisSession = false
        navController.navigate(Destinations.AUTH_GRAPH) {
            popUpTo(Destinations.MAIN_GRAPH) { inclusive = true }
        }
    }

    // ROOT CAUSE FOUND 2026-09-10 for user-reported "can't login now, missing or insufficient
    // permissions" - a SECOND bug on top of the one already fixed below (which wrapped the
    // consume() call in remember{} at its own call site, inside the `if (needsAppLockSetup)`
    // block). That remember{} scope is NOT stable across every recomposition: needsAppLockSetup
    // itself briefly flickers false->true->false during the same login sequence (isLoggedIn/
    // hasCompletedAppLockSetup settle from their own separate collectAsState flows one after the
    // other, not atomically), and each time the `if` block's condition goes false then true
    // again, Compose tears down and recreates that whole remember{} scope from scratch - calling
    // consume() a second time, which returns null since the first (correct) call already cleared
    // it, hitting the "no credentials, sign out" fallback and logging the user right back out
    // moments after a successful sign-in - exactly what the logcat evidence showed (sign-in,
    // sign-out ~11ms later, then the Firestore listener predictably gets PERMISSION_DENIED
    // against an already-signed-out session).
    //
    // Fixed properly this time by hoisting the read to THIS level (outside the conditional
    // block entirely) in a LaunchedEffect keyed on isLoggedIn - it only runs once per actual
    // sign-in transition (false/null -> true), not once per recomposition of a conditional
    // block, and its result is held in a mutableStateOf that survives needsAppLockSetup
    // flickering, since it lives above that condition, not inside it.
    var pendingAppLockCredentials by remember {
        mutableStateOf<com.king.mob_app_assignment.util.PendingAppLockCredentials.Credentials?>(null)
    }
    var hasConsumedPendingCredentials by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isLoggedIn) {
        if (isLoggedIn == true && !hasConsumedPendingCredentials) {
            hasConsumedPendingCredentials = true
            pendingAppLockCredentials = com.king.mob_app_assignment.util.PendingAppLockCredentials.consume()
        } else if (isLoggedIn != true) {
            // Signed out (or not yet known) - reset so the NEXT sign-in gets a fresh consume()
            // instead of reusing a stale result or staying permanently latched from this one.
            hasConsumedPendingCredentials = false
            pendingAppLockCredentials = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startGraph) {
            onboardingGraph(navController, isLoggedIn, onOnboardingFinished, onPrivacyNoticeAcknowledged)
            authGraph(navController)
            mainGraph(navController, isAdmin)
        }

        if (needsAppLockSetup) {
            // Only act once hasConsumedPendingCredentials is actually true - i.e. the
            // LaunchedEffect above has run at least once for this sign-in. Before that, both
            // pendingAppLockCredentials and hasConsumedPendingCredentials start false/null, which
            // would otherwise look identical to "consumed and genuinely empty" and incorrectly
            // trigger the sign-out fallback on the very first frame, before the effect has even
            // had a chance to run.
            if (!hasConsumedPendingCredentials) {
                // Still waiting for the LaunchedEffect above to resolve - render nothing rather
                // than a flash of either SetupAppLockScreen or a premature sign-out.
            } else if (pendingAppLockCredentials == null) {
                // Should not happen in practice (setup always follows right after a live login/
                // register call in the same session that set PendingAppLockCredentials), but a
                // process death between login and setup completing is possible in theory - safe
                // fallback is to sign the user back out rather than get stuck on a setup screen
                // with no credentials to enroll biometric with (PIN alone still needs SOME
                // password on file to be meaningful, since app-lock gates access to a real
                // signed-in account).
                androidx.compose.runtime.LaunchedEffect(Unit) { onSignOutWithNavigation() }
            } else {
                val pending = pendingAppLockCredentials!!
                com.king.mob_app_assignment.ui.auth.SetupAppLockScreen(
                    accountEmail = pending.email,
                    accountPassword = pending.password,
                    onSetupComplete = { markAppUnlocked() },
                )
            }
        } else if (needsAppLockUnlock) {
            com.king.mob_app_assignment.ui.auth.AppLockScreen(
                onUnlocked = { markAppUnlocked() },
                onSignOutInstead = onSignOutWithNavigation,
            )
        } else if (isInMainGraph && !permissionGateResolved) {
            com.king.mob_app_assignment.ui.permissions.StartupPermissionGate(
                onAllResolved = { permissionGateResolved = true },
            )
        }

        // Hoisted above mainGraph's per-screen composables (rather than inside MainScaffold) so
        // it can float over Home's own content. Restricted to the Home screen only - it used to
        // appear on every main-graph screen (including ReportDetail), where a fixed bottom-right
        // position ended up sitting on top of screen-specific content lower on the screen (e.g.
        // the AI-review card's "Review and resubmit" button) with no reliable way to know from
        // here what each individual screen has near that corner. Draggable so it can also be
        // moved out of the way of whatever IS on Home, rather than picking one more fixed spot
        // that might still collide with something.
        if (isLoggedIn) {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            if (currentRoute == Destinations.HOME) {
                val density = LocalDensity.current
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }
                // Default resting position: bottom-end, same corner it always occupied - only
                // the ability to drag it elsewhere is new, not the default placement.
                //
                // BUG FIX (found via manual testing, 2026-11-18): 88.dp only cleared the bottom
                // nav bar, not Home's own "Shake in background" card, which sits directly above
                // it and extends the same width as the screen - the FAB's default position landed
                // squarely on top of that card's toggle switch and covered its description text
                // every single time Home was (re)opened, not just in some edge case. The drag
                // escape hatch above existed for exactly this risk, but doesn't help if the
                // default spot collides on first load before a user has any reason to drag it.
                // 320.dp clears both the "Shake in background" card AND the "New Report" button
                // above it (confirmed visually against a real device screenshot - 200.dp still
                // touched both), rather than just nudging past the first guess.
                val defaultOffsetPx = with(density) { 16.dp.toPx() to 320.dp.toPx() }

                FloatingActionButton(
                    onClick = { navController.navigate(Destinations.CHAT) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset {
                            IntOffset(
                                (-defaultOffsetPx.first + offsetX).toInt(),
                                (-defaultOffsetPx.second + offsetY).toInt(),
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        },
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = "Help assistant")
                }
            }
        }
    }
}

private fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    isLoggedIn: Boolean,
    onOnboardingFinished: () -> Unit,
    onPrivacyNoticeAcknowledged: () -> Unit,
) {
    // Privacy notice always follows onboarding (never the reverse) - a fresh install goes
    // walkthrough -> privacy notice -> auth/main, same order the feature was designed around
    // (see PrivacyNoticeScreen's doc comment). A returning user who already finished onboarding
    // before this screen existed instead lands directly in PRIVACY_NOTICE_GRAPH (see
    // RootNavGraph's startGraph decision), skipping the walkthrough entirely since
    // hasCompletedOnboarding is independently true for them already.
    navigation(startDestination = Destinations.ONBOARDING, route = Destinations.ONBOARDING_GRAPH) {
        composable(Destinations.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingFinished()
                    navController.navigate(Destinations.PRIVACY_NOTICE_GRAPH) {
                        popUpTo(Destinations.ONBOARDING_GRAPH) { inclusive = true }
                    }
                },
            )
        }
    }
    navigation(startDestination = Destinations.PRIVACY_NOTICE, route = Destinations.PRIVACY_NOTICE_GRAPH) {
        composable(Destinations.PRIVACY_NOTICE) {
            com.king.mob_app_assignment.ui.onboarding.PrivacyNoticeScreen(
                onAcknowledge = {
                    onPrivacyNoticeAcknowledged()
                    val nextGraph = if (isLoggedIn) Destinations.MAIN_GRAPH else Destinations.AUTH_GRAPH
                    navController.navigate(nextGraph) {
                        popUpTo(Destinations.PRIVACY_NOTICE_GRAPH) { inclusive = true }
                    }
                },
            )
        }
    }
}

private fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation(startDestination = Destinations.LOGIN, route = Destinations.AUTH_GRAPH) {
        composable(Destinations.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Destinations.MAIN_GRAPH) {
                        popUpTo(Destinations.AUTH_GRAPH) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Destinations.REGISTER) },
                onNavigateToForgotCredentials = { navController.navigate(Destinations.FORGOT_CREDENTIALS) },
            )
        }
        composable(Destinations.FORGOT_CREDENTIALS) {
            ForgotCredentialsScreen(onBackClick = { navController.popBackStack() })
        }
        composable(Destinations.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Destinations.MAIN_GRAPH) {
                        popUpTo(Destinations.AUTH_GRAPH) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    isAdmin: Boolean,
) {
    navigation(startDestination = Destinations.HOME, route = Destinations.MAIN_GRAPH) {
        composable(Destinations.HOME) {
            MainScaffold(navController = navController, isAdmin = isAdmin) {
                HomeScreen(
                    isAdmin = isAdmin,
                    onNavigateToCapture = { viaShake ->
                        navController.navigate(Destinations.capture(viaShake = viaShake))
                    },
                )
            }
        }
        composable(Destinations.MY_REPORTS) {
            MainScaffold(navController = navController, isAdmin = isAdmin) {
                MyReportsScreen(
                    onReportClick = { report -> navController.navigate(Destinations.reportDetail(report.id)) },
                )
            }
        }
        composable(Destinations.MAP) {
            MainScaffold(navController = navController, isAdmin = isAdmin) {
                com.king.mob_app_assignment.ui.map.MapScreen()
            }
        }
        composable(Destinations.PROFILE) {
            MainScaffold(navController = navController, isAdmin = isAdmin) {
                ProfileScreen(
                    onLoggedOut = {
                        navController.navigate(Destinations.AUTH_GRAPH) {
                            popUpTo(Destinations.MAIN_GRAPH) { inclusive = true }
                        }
                    },
                    onOpenPermissionDashboard = { navController.navigate(Destinations.PERMISSION_DASHBOARD) },
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                    onContactSupportClick = { navController.navigate(Destinations.CONTACT_SUPPORT) },
                    onOpenNotificationSettings = { navController.navigate(Destinations.NOTIFICATION_SETTINGS) },
                )
            }
        }
        composable(Destinations.ADMIN_REPORTS) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                MainScaffold(navController = navController, isAdmin = isAdmin) {
                    AdminReportsScreen(
                        onReportClick = { report -> navController.navigate(Destinations.reportDetail(report.id)) },
                    )
                }
            }
        }
        composable(Destinations.ADMIN_DASHBOARD) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                MainScaffold(navController = navController, isAdmin = isAdmin) {
                    AdminDashboardScreen(
                        onReportClick = { report -> navController.navigate(Destinations.reportDetail(report.id)) },
                        onIncidentClick = { incident -> navController.navigate(Destinations.incidentDetail(incident.id)) },
                        onViewAllIncidents = { navController.navigate(Destinations.ADMIN_INCIDENTS) },
                        onViewRiskAreas = { navController.navigate(Destinations.ADMIN_RISK_AREAS) },
                        onPromoteAdminClick = { navController.navigate(Destinations.PROMOTE_ADMIN) },
                    )
                }
            }
        }
        composable(Destinations.ADMIN_SUPPORT_MESSAGES) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                MainScaffold(navController = navController, isAdmin = isAdmin) {
                    AdminSupportMessagesScreen(
                        onOpenTicket = { ticketId ->
                            navController.navigate(Destinations.supportThread(ticketId, isAdmin = true))
                        },
                    )
                }
            }
        }
        composable(Destinations.ADMIN_INCIDENTS) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                MainScaffold(navController = navController, isAdmin = isAdmin) {
                    AdminIncidentsScreen(
                        onIncidentClick = { incident -> navController.navigate(Destinations.incidentDetail(incident.id)) },
                    )
                }
            }
        }
        composable(Destinations.INCIDENT_DETAIL_ROUTE) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                IncidentDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onReportClick = { reportId -> navController.navigate(Destinations.reportDetail(reportId)) },
                )
            }
        }
        composable(Destinations.ADMIN_RISK_AREAS) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                MainScaffold(navController = navController, isAdmin = isAdmin) {
                    AdminRiskAreasScreen(
                        onRiskAreaClick = { riskScore -> navController.navigate(Destinations.riskAreaDetail(riskScore.id)) },
                    )
                }
            }
        }
        composable(Destinations.RISK_AREA_DETAIL_ROUTE) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                RiskAreaDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onReportClick = { reportId -> navController.navigate(Destinations.reportDetail(reportId)) },
                )
            }
        }
        composable(Destinations.PROMOTE_ADMIN) {
            AdminRouteGuard(isAdmin = isAdmin, navController = navController) {
                com.king.mob_app_assignment.ui.admin.PromoteAdminScreen(
                    onBackClick = { navController.popBackStack() },
                )
            }
        }
        composable(Destinations.CONTACT_SUPPORT) {
            ContactSupportScreen(
                onBackClick = { navController.popBackStack() },
                onOpenTicket = { ticketId ->
                    navController.navigate(Destinations.supportThread(ticketId, isAdmin = false))
                },
            )
        }
        composable(
            Destinations.SUPPORT_THREAD_ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("ticketId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("isAdmin") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            com.king.mob_app_assignment.ui.support.SupportThreadScreen(
                isAdmin = backStackEntry.arguments?.getBoolean("isAdmin") ?: false,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Destinations.NOTIFICATION_SETTINGS) {
            com.king.mob_app_assignment.ui.notifications.NotificationSettingsScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onOpenPermissionDashboard = { navController.navigate(Destinations.PERMISSION_DASHBOARD) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Destinations.PERMISSION_DASHBOARD) {
            val context = LocalContext.current
            val application = remember(context) { context.applicationContext as android.app.Application }
            val factory = remember(application) {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return PermissionDashboardViewModel(application) as T
                    }
                }
            }
            val viewModel: PermissionDashboardViewModel = viewModel(factory = factory)
            PermissionDashboardScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            Destinations.CAPTURE,
            arguments = listOf(
                androidx.navigation.navArgument("viaShake") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            CaptureScreen(
                viaShake = backStackEntry.arguments?.getBoolean("viaShake") ?: false,
                onDismiss = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
                onViewExistingReport = { reportId ->
                    navController.navigate(Destinations.reportDetail(reportId)) {
                        popUpTo(Destinations.CAPTURE) { inclusive = true }
                    }
                },
            )
        }
        composable(Destinations.REPORT_DETAIL_ROUTE) {
            ReportDetailScreen(onBackClick = { navController.popBackStack() })
        }
        composable(Destinations.CHAT) {
            ChatScreen(onBackClick = { navController.popBackStack() })
        }
    }
}

// Client-side defense-in-depth on top of firestore.rules' isAdmin() server-side check: before
// this, admin routes were only ever hidden from the bottom nav - a non-admin who somehow
// deep-linked the route string directly would still render the admin composable (even though
// any Firestore reads inside it would then fail server-side). This redirects them out of the
// admin route entirely the instant isAdmin is false, rather than relying solely on nav-tab
// visibility to keep them away.
@Composable
private fun AdminRouteGuard(
    isAdmin: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(isAdmin) {
        if (!isAdmin) {
            navController.navigate(Destinations.HOME) {
                popUpTo(Destinations.MAIN_GRAPH) { inclusive = false }
            }
        }
    }
    if (isAdmin) {
        content()
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeCount: Int = 0,
)

@Composable
private fun MainScaffold(
    navController: NavHostController,
    isAdmin: Boolean,
    content: @Composable () -> Unit,
) {
    val homeLabel = stringResource(R.string.nav_home)
    val myReportsLabel = stringResource(R.string.nav_my_reports)
    val mapLabel = stringResource(R.string.nav_map)
    val profileLabel = stringResource(R.string.nav_profile)
    val adminLabel = stringResource(R.string.nav_admin)
    val adminDashboardLabel = stringResource(R.string.nav_admin_dashboard)
    val adminSupportLabel = stringResource(R.string.nav_admin_support)

    val badgeViewModel: PendingReportsBadgeViewModel = hiltViewModel()
    val pendingCount by badgeViewModel.pendingCount.collectAsState()
    val supportUnreadBadgeViewModel: com.king.mob_app_assignment.ui.nav.SupportUnreadBadgeViewModel = hiltViewModel()
    val supportUnreadCount by supportUnreadBadgeViewModel.unreadCount.collectAsState()

    val items = remember(
        isAdmin, homeLabel, myReportsLabel, mapLabel, profileLabel, adminLabel, adminDashboardLabel,
        adminSupportLabel, pendingCount, supportUnreadCount,
    ) {
        buildList {
            add(BottomNavItem(Destinations.HOME, homeLabel, Icons.Filled.Home))
            if (isAdmin) {
                // An admin manages every citizen's reports through ADMIN_REPORTS below, not a
                // personal report history of their own - MY_REPORTS (and its "file a new report"
                // entry point) is citizen-only.
                add(BottomNavItem(Destinations.ADMIN_REPORTS, adminLabel, Icons.Filled.AdminPanelSettings))
                add(BottomNavItem(Destinations.ADMIN_DASHBOARD, adminDashboardLabel, Icons.Filled.Dashboard))
                add(BottomNavItem(Destinations.ADMIN_SUPPORT_MESSAGES, adminSupportLabel, Icons.Filled.SupportAgent))
            } else {
                add(BottomNavItem(Destinations.MY_REPORTS, myReportsLabel, Icons.Filled.Assignment, badgeCount = pendingCount))
            }
            // Map is a community-wide view (every citizen's reports plotted, not just the signed-
            // in account's own) - shown to both citizens and admins alike, same reports collection
            // either way (firestore.rules already makes it isSignedIn()-readable for anyone).
            add(BottomNavItem(Destinations.MAP, mapLabel, Icons.Filled.Map))
            // Profile always last - the settings/account-management tab, kept at the end of the
            // row regardless of how many admin-only tabs precede it for a citizen vs. an admin.
            add(BottomNavItem(Destinations.PROFILE, profileLabel, Icons.Filled.Person, badgeCount = supportUnreadCount))
        }
    }

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                items.forEach { item ->
                    val selected = currentRoute?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (item.badgeCount > 0) {
                                BadgedBox(badge = { Badge { Text(item.badgeCount.toString()) } }) {
                                    androidx.compose.material3.Icon(item.icon, contentDescription = item.label)
                                }
                            } else {
                                androidx.compose.material3.Icon(item.icon, contentDescription = item.label)
                            }
                        },
                        // BUG FIX (found via manual testing, 2026-11-20): with 6 items in an
                        // Admin session's nav bar (vs. 4 for a Citizen), NavigationBar divides
                        // the available width evenly across all of them, leaving noticeably less
                        // per-item space than the Citizen layout was ever tested with - long
                        // labels like "Dashboard" wrapped onto two lines ("Dashboa"/"rd"),
                        // breaking the single-line layout every other item still had. maxLines=1
                        // with an ellipsis fallback keeps every label on one line regardless of
                        // how many items are shown or how narrow the screen is.
                        label = {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
