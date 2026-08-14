package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportMediaType
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.reportReferenceCode
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.StatusBadge
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportSortOrder { NEWEST_FIRST, OLDEST_FIRST }

data class ReportFilterState(
    val statusFilter: ReportStatus? = null,
    val categoryFilter: ReportCategory? = null,
    val sortOrder: ReportSortOrder = ReportSortOrder.NEWEST_FIRST,
    val searchQuery: String = "",
    val needsAiReviewOnly: Boolean = false,
)

val ReportFilterState.isActive: Boolean
    get() = statusFilter != null || categoryFilter != null || searchQuery.isNotBlank() || needsAiReviewOnly

fun List<Report>.applyFilter(filter: ReportFilterState): List<Report> {
    val query = filter.searchQuery.trim()
    val filtered = this
        .filter { filter.statusFilter == null || it.status == filter.statusFilter }
        .filter { filter.categoryFilter == null || it.category == filter.categoryFilter }
        .filter { !filter.needsAiReviewOnly || it.needsAiReview }
        .filter {
            query.isBlank() ||
                it.address.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true)
        }
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
    ReportCategory.CUSTOM -> Icons.Filled.Edit
    ReportCategory.UNCATEGORIZED -> Icons.Filled.HourglassEmpty
}

fun ReportCategory.displayLabel(): String = when (this) {
    ReportCategory.POTHOLE -> "Pothole"
    ReportCategory.STREETLIGHT -> "Streetlight"
    ReportCategory.GARBAGE -> "Garbage"
    ReportCategory.WATER_LEAK -> "Water leak"
    ReportCategory.OTHER -> "Other"
    ReportCategory.CUSTOM -> "Custom"
    ReportCategory.UNCATEGORIZED -> "Uncategorized"
}

/** Shows the user/AI-provided custom label when category == CUSTOM, else the fixed display label. */
fun Report.categoryDisplayText(): String =
    if (category == ReportCategory.CUSTOM && customLabel.isNotBlank()) customLabel else category.displayLabel()

/** Color for the small priority dot, reusing existing theme colors (no bespoke palette). */
fun ReportPriority.dotColor(colorScheme: androidx.compose.material3.ColorScheme): Color = when (this) {
    ReportPriority.LOW -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    ReportPriority.NORMAL -> colorScheme.onSurfaceVariant
    ReportPriority.HIGH -> colorScheme.secondary
    ReportPriority.URGENT -> colorScheme.error
}

fun ReportPriority.displayLabel(): String = when (this) {
    ReportPriority.LOW -> "Low"
    ReportPriority.NORMAL -> "Normal"
    ReportPriority.HIGH -> "High"
    ReportPriority.URGENT -> "Urgent"
}

/** Compact glanceable priority indicator: a colored dot + label, used in admin list rows. */
@Composable
fun PriorityIndicator(priority: ReportPriority, modifier: Modifier = Modifier) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = modifier) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = priority.dotColor(MaterialTheme.colorScheme), shape = CircleShape),
        )
        Text(
            text = priority.displayLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Compact department label for admin list rows: "Unassigned" when null. */
@Composable
fun DepartmentLabel(department: Department?, modifier: Modifier = Modifier) {
    Text(
        text = department?.displayLabel() ?: "Unassigned",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ReportSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search by address or note") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
    )
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

/**
 * Thumbs-up control shown next to a report - lets a citizen signal "I see this too" without
 * filing a duplicate. Only clickable when the viewer isn't the report's owner (upvoting your
 * own report doesn't make sense); otherwise renders as a plain read-only count.
 */
@Composable
fun UpvoteButton(
    upvoteCount: Int,
    isUpvotedByMe: Boolean,
    canUpvote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = modifier.let { if (canUpvote) it.clickable(onClick = onClick) else it },
    ) {
        Icon(
            imageVector = if (isUpvotedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (isUpvotedByMe) "Remove upvote" else "Upvote this report",
            tint = if (isUpvotedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = upvoteCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (isUpvotedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
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
                    Text(text = report.categoryDisplayText(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = reportReferenceCode(report.id),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        StatusBadge(status = report.status)
                        if (report.mediaType == ReportMediaType.VIDEO) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Video report",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).padding(start = 8.dp),
                            )
                        }
                        if (report.isAnonymous) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = "Anonymous report",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).padding(start = 8.dp),
                            )
                        }
                        if (report.needsAiReview) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Needs your input",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp).padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            trailingContent?.invoke()
        }
    }
}
