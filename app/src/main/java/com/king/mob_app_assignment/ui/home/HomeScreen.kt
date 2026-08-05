package com.king.mob_app_assignment.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.components.SkeletonList
import com.king.mob_app_assignment.ui.components.StatsRow
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
        topBar = { AppTopBar(title = stringResource(R.string.home_top_bar_title)) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            PrimaryActionButton(
                text = stringResource(R.string.home_report_issue_button),
                onClick = onReportIssueClick,
                icon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
            )

            Text(
                text = stringResource(R.string.home_shake_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )

            if (uiState.pendingSyncCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "Waiting to sync",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            val plural = if (uiState.pendingSyncCount == 1) "report" else "reports"
                            Text(
                                text = "${uiState.pendingSyncCount} $plural saved on this device only - " +
                                    "they'll upload automatically once you're back online.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            StatsRow(stats = uiState.impactStats, modifier = Modifier.padding(bottom = 24.dp))

            Text(text = stringResource(R.string.home_recent_activity), style = MaterialTheme.typography.titleMedium)

            when {
                uiState.isLoading -> SkeletonList(count = 3, modifier = Modifier.padding(top = 8.dp))
                uiState.recentReports.isEmpty() -> EmptyState(
                    title = stringResource(R.string.home_no_reports_title),
                    message = stringResource(R.string.home_no_reports_message),
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
