package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.ReportFilterBar
import com.king.mob_app_assignment.ui.reports.ReportListItem
import com.king.mob_app_assignment.ui.reports.StatusDropdown

@Composable
fun AdminReportsScreen(
    onReportClick: (Report) -> Unit,
    viewModel: AdminReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "All Reports (Admin)") },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ReportFilterBar(filter = uiState.filter, onFilterChange = viewModel::onFilterChange)

            if (uiState.isLoading) {
                LoadingIndicator()
            } else if (uiState.reports.isEmpty()) {
                EmptyState(message = "No reports match the current filters.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportListItem(
                            report = report,
                            onClick = { onReportClick(report) },
                            trailingContent = {
                                StatusDropdown(
                                    currentStatus = report.status,
                                    onStatusSelected = { viewModel.updateStatus(report.id, it) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
