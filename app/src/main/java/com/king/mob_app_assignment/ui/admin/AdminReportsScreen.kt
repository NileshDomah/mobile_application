package com.king.mob_app_assignment.ui.admin

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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.SkeletonList
import com.king.mob_app_assignment.ui.reports.DepartmentLabel
import com.king.mob_app_assignment.ui.reports.PriorityIndicator
import com.king.mob_app_assignment.ui.reports.ReportFilterBar
import com.king.mob_app_assignment.ui.reports.ReportListItem
import com.king.mob_app_assignment.ui.reports.ReportSearchField
import com.king.mob_app_assignment.ui.reports.StatusDropdown
import com.king.mob_app_assignment.ui.reports.isActive
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsScreen(
    onReportClick: (Report) -> Unit,
    viewModel: AdminReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val retryLabel = translatedStringResource(R.string.admin_reports_retry)
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = retryLabel)
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.admin_reports_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ReportSearchField(
                query = uiState.filter.searchQuery,
                onQueryChange = { query -> viewModel.onFilterChange(uiState.filter.copy(searchQuery = query)) },
            )
            ReportFilterBar(filter = uiState.filter, onFilterChange = viewModel::onFilterChange)

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isLoading) {
                    SkeletonList()
                } else if (uiState.reports.isEmpty()) {
                    if (uiState.filter.isActive) {
                        EmptyState(
                            title = translatedStringResource(R.string.admin_reports_no_matching_title),
                            message = translatedStringResource(R.string.admin_reports_no_matching_message),
                        )
                    } else {
                        EmptyState(message = translatedStringResource(R.string.admin_reports_empty))
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.reports, key = { it.id }) { report ->
                            ReportListItem(
                                report = report,
                                isAdmin = true,
                                onClick = { onReportClick(report) },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        DepartmentLabel(department = report.department)
                                        PriorityIndicator(
                                            priority = report.priority,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                                        )
                                        StatusDropdown(
                                            currentStatus = report.status,
                                            onStatusSelected = { viewModel.updateStatus(report.id, it) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
