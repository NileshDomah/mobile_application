package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.ReportListItem
import com.king.mob_app_assignment.ui.reports.displayLabel as categoryDisplayLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
fun RiskAreaDetailScreen(
    onBackClick: () -> Unit,
    onReportClick: (String) -> Unit,
    viewModel: RiskAreaDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.risk_area_detail_title), onBackClick = onBackClick) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { LoadingIndicator() }
            return@Scaffold
        }
        val riskScore = uiState.riskScore
        if (riskScore == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(translatedStringResource(R.string.risk_area_detail_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(text = riskScore.areaLabel, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = riskScore.category.categoryDisplayLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (riskScore.insufficientData) {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(text = translatedStringResource(R.string.risk_area_detail_insufficient_data_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = riskScore.recommendation.ifBlank {
                                translatedStringResource(R.string.risk_area_detail_insufficient_data_message)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                // Transparent score breakdown - same "show every factor" convention as
                // IncidentDetailScreen's priority score card, per the brief's explicit
                // requirement that risk scoring be explainable, not a black box.
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = translatedStringResource(R.string.risk_area_detail_score_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = translatedStringResource(R.string.risk_area_detail_score_value, riskScore.score, riskScore.level.displayLabel()),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        riskScore.factors.forEach { factor ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = factor.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = factor.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(text = translatedStringResource(R.string.risk_area_detail_points, factor.points), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(text = translatedStringResource(R.string.risk_area_detail_recommendation_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = riskScore.recommendation,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (riskScore.isRecurring) {
                            Text(
                                text = translatedStringResource(R.string.risk_area_detail_recurring_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                if (riskScore.history.size > 1) {
                    RiskTrendSection(history = riskScore.history)
                }
            }

            Column {
                Text(text = translatedStringResource(R.string.risk_area_detail_contributing_reports, uiState.contributingReports.size), style = MaterialTheme.typography.titleMedium)
                uiState.contributingReports.forEach { report ->
                    ReportListItem(
                        report = report,
                        isAdmin = true,
                        onClick = { onReportClick(report.id) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskTrendSection(history: List<com.king.mob_app_assignment.domain.model.RiskHistoryPoint>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = translatedStringResource(R.string.risk_area_detail_trend_title), style = MaterialTheme.typography.titleMedium)
            history.takeLast(10).forEach { point ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = dateFormat.format(Date(point.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${point.score}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
