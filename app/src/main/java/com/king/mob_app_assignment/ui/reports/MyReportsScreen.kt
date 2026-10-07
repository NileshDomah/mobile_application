package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.SkeletonList
import com.king.mob_app_assignment.ui.theme.translatedStringResource

/**
 * The citizen's own reports only - the global/community "all reports" list+map (formerly the
 * "Reports" bottom-nav tab, and its 5-layer Digital Map) has been removed entirely per an
 * explicit request to scope citizens down to just their own reports. Category/status filter
 * chips are also intentionally gone - the only navigation left is the Solved/Not Solved split
 * below, which is the one distinction that actually matters to a citizen checking on their own
 * reports (did it get fixed or not), not the finer admin-facing SUBMITTED/IN_REVIEW/RESOLVED
 * pipeline detail.
 *
 * "Solved" means [ReportStatus.RESOLVED] - the only way a report reaches that status is an admin
 * explicitly marking it resolved (with a resolution note) via the existing admin resolve flow;
 * there's no separate "mark as solved" action introduced here, this just presents the same
 * status the admin already sets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen(
    onReportClick: (Report) -> Unit,
    viewModel: ReportsListViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.setScope(ReportsScope.MINE) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    val retryLabel = translatedStringResource(R.string.my_reports_retry)
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = retryLabel)
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.refresh()
        }
    }

    val notSolved = uiState.reports.filter { it.status != ReportStatus.RESOLVED }
    val solved = uiState.reports.filter { it.status == ReportStatus.RESOLVED }
    val visibleReports = if (selectedTab == 0) notSolved else solved

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.my_reports_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(translatedStringResource(R.string.my_reports_tab_not_solved, notSolved.size)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(translatedStringResource(R.string.my_reports_tab_solved, solved.size)) },
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            ) {
                if (uiState.isLoading) {
                    SkeletonList()
                } else if (visibleReports.isEmpty()) {
                    EmptyState(
                        message = if (selectedTab == 0) {
                            translatedStringResource(R.string.my_reports_empty_not_solved)
                        } else {
                            translatedStringResource(R.string.my_reports_empty_solved)
                        },
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(visibleReports, key = { it.id }) { report ->
                            ReportListItem(report = report, onClick = { onReportClick(report) })
                        }
                    }
                }
            }
        }
    }
}
