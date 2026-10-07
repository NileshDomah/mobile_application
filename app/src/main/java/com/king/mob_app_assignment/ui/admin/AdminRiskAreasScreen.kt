package com.king.mob_app_assignment.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.RiskLevel
import com.king.mob_app_assignment.domain.model.RiskScore
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.reports.displayLabel as categoryDisplayLabel
import com.king.mob_app_assignment.ui.theme.translatedStringResource

@Composable
fun AdminRiskAreasScreen(
    onRiskAreaClick: (RiskScore) -> Unit,
    viewModel: AdminRiskAreasViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.admin_risk_areas_title)) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                text = translatedStringResource(R.string.admin_risk_areas_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
                selected = uiState.showInsufficientData,
                onClick = { viewModel.onShowInsufficientToggle(!uiState.showInsufficientData) },
                label = { Text(translatedStringResource(R.string.admin_risk_areas_show_insufficient)) },
                modifier = Modifier.padding(top = 8.dp),
            )

            if (uiState.isLoading) {
                LoadingIndicator()
            } else if (uiState.riskScores.isEmpty()) {
                EmptyState(
                    message = translatedStringResource(R.string.admin_risk_areas_empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.riskScores, key = { it.id }) { riskScore ->
                        RiskScoreListItem(riskScore = riskScore, onClick = { onRiskAreaClick(riskScore) })
                    }
                }
            }
        }
    }
}

@Composable
fun RiskScoreListItem(riskScore: RiskScore, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = riskScore.areaLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                RiskLevelBadge(riskScore)
            }
            Text(
                text = riskScore.category.categoryDisplayLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (riskScore.insufficientData) {
                Text(
                    text = translatedStringResource(R.string.admin_risk_areas_insufficient_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = translatedStringResource(R.string.admin_risk_areas_report_count, riskScore.contributingReportIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = translatedStringResource(R.string.admin_risk_areas_unresolved_count, riskScore.unresolvedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (riskScore.isRecurring) {
                        Text(
                            text = translatedStringResource(R.string.admin_risk_areas_recurring),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RiskLevelBadge(riskScore: RiskScore) {
    if (riskScore.insufficientData) {
        Text(text = translatedStringResource(R.string.admin_risk_areas_no_data), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val color = when (riskScore.level) {
        RiskLevel.LOW -> MaterialTheme.colorScheme.primary
        RiskLevel.MODERATE -> MaterialTheme.colorScheme.tertiary
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color = color, shape = CircleShape))
        Text(
            text = "${riskScore.level.displayLabel()} (${riskScore.score})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
