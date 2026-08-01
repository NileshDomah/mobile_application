package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportSortOrder { NEWEST_FIRST, OLDEST_FIRST }

data class ReportFilterState(
    val statusFilter: ReportStatus? = null,
    val categoryFilter: ReportCategory? = null,
    val sortOrder: ReportSortOrder = ReportSortOrder.NEWEST_FIRST,
)

fun List<Report>.applyFilter(filter: ReportFilterState): List<Report> {
    val filtered = this
        .filter { filter.statusFilter == null || it.status == filter.statusFilter }
        .filter { filter.categoryFilter == null || it.category == filter.categoryFilter }
    return when (filter.sortOrder) {
        ReportSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.timestamp }
        ReportSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestamp }
    }
}

private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> "Submitted"
    ReportStatus.IN_REVIEW -> "In review"
    ReportStatus.RESOLVED -> "Resolved"
}

fun ReportCategory.icon(): ImageVector = when (this) {
    ReportCategory.POTHOLE -> Icons.Filled.Construction
    ReportCategory.STREETLIGHT -> Icons.Filled.Lightbulb
    ReportCategory.GARBAGE -> Icons.Filled.Delete
    ReportCategory.WATER_LEAK -> Icons.Filled.WaterDrop
    ReportCategory.OTHER -> Icons.Filled.Help
}

fun ReportCategory.displayLabel(): String = when (this) {
    ReportCategory.POTHOLE -> "Pothole"
    ReportCategory.STREETLIGHT -> "Streetlight"
    ReportCategory.GARBAGE -> "Garbage"
    ReportCategory.WATER_LEAK -> "Water leak"
    ReportCategory.OTHER -> "Other"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFilterBar(
    filter: ReportFilterState,
    onFilterChange: (ReportFilterState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CategoryChip(
                    label = "All statuses",
                    selected = filter.statusFilter == null,
                    onClick = { onFilterChange(filter.copy(statusFilter = null)) },
                )
            }
            items(ReportStatus.entries.toList()) { status ->
                CategoryChip(
                    label = status.label(),
                    selected = filter.statusFilter == status,
                    onClick = {
                        onFilterChange(filter.copy(statusFilter = if (filter.statusFilter == status) null else status))
                    },
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            item {
                CategoryChip(
                    label = "All categories",
                    selected = filter.categoryFilter == null,
                    onClick = { onFilterChange(filter.copy(categoryFilter = null)) },
                )
            }
            items(ReportCategory.entries.toList()) { category ->
                CategoryChip(
                    label = category.displayLabel(),
                    selected = filter.categoryFilter == category,
                    onClick = {
                        onFilterChange(filter.copy(categoryFilter = if (filter.categoryFilter == category) null else category))
                    },
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            item {
                AssistChip(
                    onClick = {
                        val next = if (filter.sortOrder == ReportSortOrder.NEWEST_FIRST) {
                            ReportSortOrder.OLDEST_FIRST
                        } else {
                            ReportSortOrder.NEWEST_FIRST
                        }
                        onFilterChange(filter.copy(sortOrder = next))
                    },
                    label = {
                        Text(if (filter.sortOrder == ReportSortOrder.NEWEST_FIRST) "Newest first" else "Oldest first")
                    },
                    leadingIcon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

private val listItemDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

@Composable
fun ReportListItem(
    report: Report,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = report.category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = report.category.displayLabel(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = report.address.ifBlank { "${report.latitude}, ${report.longitude}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = listItemDateFormat.format(Date(report.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusBadge(status = report.status)
                }
            }
            trailingContent?.invoke()
        }
    }
}
