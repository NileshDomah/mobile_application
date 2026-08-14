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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.SkeletonList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsListScreen(
    title: String,
    scope: ReportsScope,
    onReportClick: (Report) -> Unit,
    viewModel: ReportsListViewModel = hiltViewModel(),
) {
    LaunchedEffect(scope) { viewModel.setScope(scope) }
    val uiState by viewModel.uiState.collectAsState()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = "Retry")
        viewModel.dismissError()
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = title) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ReportSearchField(
                query = uiState.filter.searchQuery,
                onQueryChange = { query -> viewModel.onFilterChange(uiState.filter.copy(searchQuery = query)) },
            )
            if (scope == ReportsScope.MINE) {
                CategoryChip(
                    label = "⚠ Needs your input",
                    selected = uiState.filter.needsAiReviewOnly,
                    onClick = {
                        viewModel.onFilterChange(
                            uiState.filter.copy(needsAiReviewOnly = !uiState.filter.needsAiReviewOnly),
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
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
                            title = "No matching reports",
                            message = "No reports match your search or filters. Try adjusting them.",
                        )
                    } else {
                        EmptyState(message = "No reports yet.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.reports, key = { it.id }) { report ->
                            ReportListItem(
                                report = report,
                                onClick = { onReportClick(report) },
                                trailingContent = if (scope == ReportsScope.ALL) {
                                    {
                                        UpvoteButton(
                                            upvoteCount = report.upvoteCount,
                                            isUpvotedByMe = currentUid != null && currentUid in report.upvotedByUids,
                                            canUpvote = currentUid != null && currentUid != report.userId,
                                            onClick = { viewModel.toggleUpvote(report.id) },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
