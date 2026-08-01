package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.components.StatusBadge

// No Google Maps API key is configured for this project (see PLACEHOLDERS_TODO.csv), so pins are
// rendered as a relative dot-grid canvas plus a coordinate list rather than a real map.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPlaceholderScreen(
    onReportClick: (Report) -> Unit,
    viewModel: ReportsMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Reports Map") },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        val reports = uiState.reports
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Live map view is unavailable - showing relative positions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            RelativeDotGrid(
                reports = reports,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 12.dp)
                    .clip(MaterialTheme.shapes.medium),
            )

            if (reports.isEmpty()) {
                EmptyState(message = "No reports to plot yet.")
            } else {
                LazyColumn {
                    items(reports, key = { it.id }) { report ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onReportClick(report) },
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = report.category.icon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(text = report.category.displayLabel(), style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${report.latitude}, ${report.longitude}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                StatusBadge(status = report.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelativeDotGrid(reports: List<Report>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / 6f
            for (i in 1 until 6) {
                drawLine(
                    color = gridLineColor,
                    start = Offset(i * step, 0f),
                    end = Offset(i * step, size.height),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, i * step),
                    end = Offset(size.width, i * step),
                    strokeWidth = 1f,
                )
            }
        }

        if (reports.isEmpty()) return@Box

        val lats = reports.map { it.latitude }
        val lngs = reports.map { it.longitude }
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLng = lngs.min()
        val maxLng = lngs.max()
        val latSpan = (maxLat - minLat).takeIf { it > 0.00001 } ?: 1.0
        val lngSpan = (maxLng - minLng).takeIf { it > 0.00001 } ?: 1.0

        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            reports.forEach { report ->
                val xFraction = ((report.longitude - minLng) / lngSpan).toFloat()
                val yFraction = 1f - ((report.latitude - minLat) / latSpan).toFloat()
                val center = Offset(xFraction * size.width, yFraction * size.height)
                val dotColor = if (report.status == com.king.mob_app_assignment.domain.model.ReportStatus.RESOLVED) {
                    secondaryColor
                } else {
                    primaryColor
                }
                drawCircle(color = dotColor.copy(alpha = 0.25f), radius = 18f, center = center)
                drawCircle(color = dotColor, radius = 10f, center = center)
                drawCircle(color = dotColor, radius = 10f, center = center, style = Stroke(width = 2f))
            }
        }
    }
}
