package com.king.mob_app_assignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.platform.LocalContext
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
import com.king.mob_app_assignment.ui.permissions.PermissionDashboardScreen
import com.king.mob_app_assignment.ui.permissions.PermissionDashboardViewModel
import com.king.mob_app_assignment.ui.profile.ProfileScreen
import com.king.mob_app_assignment.ui.reports.MyReportsScreen
import com.king.mob_app_assignment.ui.reports.ReportDetailScreen
import com.king.mob_app_assignment.ui.reports.ReportsTabScreen
import com.king.mob_app_assignment.ui.settings.SettingsScreen
import com.king.mob_app_assignment.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var shakeDetector: ShakeDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    // LifecycleStartEffect (re)starts collection on ON_START and cancels it on
                    // ON_STOP, which registers/unregisters the SensorManager listener inside
                    // ShakeDetector's callbackFlow via awaitClose - so the listener is bound to
                    // onResume/onPause-equivalent lifecycle states and never leaks.
                    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
                        val job = lifecycleScope.launch {
                            shakeDetector.onShakeDetected().collectLatest {
                                navController.navigate(Destinations.CAPTURE) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        onStopOrDispose { job.cancel() }
                    }

                    val currentUser by authRepository.currentUser.collectAsState(initial = null)

                    RootNavGraph(
                        navController = navController,
                        isLoggedIn = currentUser != null,
                        isAdmin = currentUser?.role == UserRole.ADMIN,
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
) {
    val startGraph = if (isLoggedIn) Destinations.MAIN_GRAPH else Destinations.AUTH_GRAPH

    NavHost(navController = navController, startDestination = startGraph) {
        authGraph(navController)
        mainGraph(navController, isAdmin)
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
            PermissionDashboardScreen(viewModel = viewModel)
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
            ReportDetailScreen()
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun MainScaffold(
    navController: NavHostController,
    isAdmin: Boolean,
    content: @Composable () -> Unit,
) {
    val items = remember(isAdmin) {
        buildList {
            add(BottomNavItem(Destinations.HOME, "Home", Icons.Filled.Home))
            add(BottomNavItem(Destinations.REPORTS, "Reports", Icons.Filled.List))
            add(BottomNavItem(Destinations.MY_REPORTS, "My Reports", Icons.Filled.Assignment))
            add(BottomNavItem(Destinations.PROFILE, "Profile", Icons.Filled.Person))
            if (isAdmin) add(BottomNavItem(Destinations.ADMIN_REPORTS, "Admin", Icons.Filled.AdminPanelSettings))
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
                        icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
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
