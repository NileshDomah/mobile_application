package com.king.mob_app_assignment.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.components.StatusBadge
import com.king.mob_app_assignment.ui.reports.ReportListItem

@Composable
fun HomeScreen(
    onReportIssueClick: () -> Unit,
    onReportClick: (Report) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Shake to Report") },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            PrimaryActionButton(
                text = "Report an Issue",
                onClick = onReportIssueClick,
                icon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
            )

            Text(
                text = "Tip: shake your phone to report instantly, wherever you are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )

            Text(text = "Recent activity", style = MaterialTheme.typography.titleMedium)

            when {
                uiState.isLoading -> LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                uiState.recentReports.isEmpty() -> EmptyState(
                    title = "No reports yet",
                    message = "You haven't filed any reports yet.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    uiState.recentReports.forEach { report ->
                        ReportListItem(
                            report = report,
                            onClick = { onReportClick(report) },
                            trailingContent = { StatusBadge(status = report.status) },
                        )
                    }
                }
            }
        }
    }
}
