package com.king.mob_app_assignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.UserRole
import com.king.mob_app_assignment.sensor.ShakeDetector
import com.king.mob_app_assignment.ui.admin.AdminReportsScreen
import com.king.mob_app_assignment.ui.auth.LoginScreen
import com.king.mob_app_assignment.ui.auth.RegisterScreen
import com.king.mob_app_assignment.ui.capture.CaptureScreen
import com.king.mob_app_assignment.ui.home.HomeScreen
import com.king.mob_app_assignment.ui.nav.Destinations
import com.king.mob_app_assignment.ui.nav.PendingReportsBadgeViewModel
import com.king.mob_app_assignment.ui.onboarding.OnboardingScreen
import com.king.mob_app_assignment.ui.permissions.PermissionDashboardScreen
import com.king.mob_app_assignment.ui.permissions.PermissionDashboardViewModel
import com.king.mob_app_assignment.ui.profile.ProfileScreen
import com.king.mob_app_assignment.ui.reports.MyReportsScreen
import com.king.mob_app_assignment.ui.reports.ReportDetailScreen
import com.king.mob_app_assignment.ui.reports.ReportsTabScreen
import com.king.mob_app_assignment.ui.settings.SettingsScreen
import com.king.mob_app_assignment.ui.theme.AppTheme
import com.king.mob_app_assignment.util.OnboardingPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var shakeDetector: ShakeDetector

    @Inject lateinit var onboardingPreferences: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val hapticFeedback = LocalHapticFeedback.current

                    // LifecycleStartEffect (re)starts collection on ON_START and cancels it on
                    // ON_STOP, which registers/unregisters the SensorManager listener inside
                    // ShakeDetector's callbackFlow via awaitClose - so the listener is bound to
                    // onResume/onPause-equivalent lifecycle states and never leaks.
                    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
                        val job = lifecycleScope.launch {
                            shakeDetector.onShakeDetected().collectLatest {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate(Destinations.CAPTURE) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        onStopOrDispose { job.cancel() }
                    }

                    val currentUser by authRepository.currentUser.collectAsState(initial = null)
                    val hasCompletedOnboarding by onboardingPreferences.hasCompletedOnboardingFlow
                        .collectAsState(initial = null)

                    RootNavGraph(
                        navController = navController,
                        isLoggedIn = currentUser != null,
                        isAdmin = currentUser?.role == UserRole.ADMIN,
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        onOnboardingFinished = {
                            lifecycleScope.launch { onboardingPreferences.setOnboardingCompleted() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootNavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean,
    isAdmin: Boolean,
    hasCompletedOnboarding: Boolean?,
    onOnboardingFinished: () -> Unit,
) {
    // hasCompletedOnboarding is null until the first DataStore emission arrives; treat that
    // briefly as "already onboarded" so returning users aren't flashed the walkthrough while
    // the flag loads - a fresh install still gets it once collectAsState settles to false.
    val startGraph = when {
        hasCompletedOnboarding == false -> Destinations.ONBOARDING_GRAPH
        isLoggedIn -> Destinations.MAIN_GRAPH
        else -> Destinations.AUTH_GRAPH
    }

    NavHost(navController = navController, startDestination = startGraph) {
        onboardingGraph(navController, isLoggedIn, onOnboardingFinished)
        authGraph(navController)
        mainGraph(navController, isAdmin)
    }
}

private fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    isLoggedIn: Boolean,
    onOnboardingFinished: () -> Unit,
) {
    navigation(startDestination = Destinations.ONBOARDING, route = Destinations.ONBOARDING_GRAPH) {
        composable(Destinations.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingFinished()
                    val nextGraph = if (isLoggedIn) Destinations.MAIN_GRAPH else Destinations.AUTH_GRAPH
                    navController.navigate(nextGraph) {
                        popUpTo(Destinations.ONBOARDING_GRAPH) { inclusive = true }
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
            )
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
                    onReportIssueClick = { navController.navigate(Destinations.CAPTURE) },
                    onReportClick = { report: Report -> navController.navigate(Destinations.reportDetail(report.id)) },
                )
            }
        }
        composable(Destinations.REPORTS) {
            MainScaffold(navController = navController, isAdmin = isAdmin) {
                ReportsTabScreen(
                    onReportClick = { report -> navController.navigate(Destinations.reportDetail(report.id)) },
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
                )
            }
        }
        composable(Destinations.ADMIN_REPORTS) {
            MainScaffold(navController = navController, isAdmin = isAdmin) {
                AdminReportsScreen(
                    onReportClick = { report -> navController.navigate(Destinations.reportDetail(report.id)) },
                )
            }
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
        composable(Destinations.CAPTURE) {
            CaptureScreen(
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
    val reportsLabel = stringResource(R.string.nav_reports)
    val myReportsLabel = stringResource(R.string.nav_my_reports)
    val profileLabel = stringResource(R.string.nav_profile)
    val adminLabel = stringResource(R.string.nav_admin)

    val badgeViewModel: PendingReportsBadgeViewModel = hiltViewModel()
    val pendingCount by badgeViewModel.pendingCount.collectAsState()

    val items = remember(isAdmin, homeLabel, reportsLabel, myReportsLabel, profileLabel, adminLabel, pendingCount) {
        buildList {
            add(BottomNavItem(Destinations.HOME, homeLabel, Icons.Filled.Home))
            add(BottomNavItem(Destinations.REPORTS, reportsLabel, Icons.Filled.List))
            add(BottomNavItem(Destinations.MY_REPORTS, myReportsLabel, Icons.Filled.Assignment, badgeCount = pendingCount))
            add(BottomNavItem(Destinations.PROFILE, profileLabel, Icons.Filled.Person))
            if (isAdmin) add(BottomNavItem(Destinations.ADMIN_REPORTS, adminLabel, Icons.Filled.AdminPanelSettings))
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
                        label = { Text(item.label) },
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
