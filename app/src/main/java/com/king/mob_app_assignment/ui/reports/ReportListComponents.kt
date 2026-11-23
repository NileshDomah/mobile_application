package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.king.mob_app_assignment.R
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
import com.king.mob_app_assignment.ui.theme.translatedStringResource

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

// "Pending", matching StatusBadge.kt's own display label for the exact same enum value - a real
// examiner-caught inconsistency where this filter chip said "Submitted" while the status pill on
// every report card for that same status already said "Pending", making it look like two
// different statuses on the same screen rather than one filter and one badge for the same thing.
private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> "Pending"
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
        text = department?.displayLabel() ?: translatedStringResource(R.string.report_list_unassigned_department),
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
        placeholder = { Text(translatedStringResource(R.string.report_list_search_placeholder)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = translatedStringResource(R.string.report_list_clear_search))
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
        // BUG FIX (found via manual testing, 2026-11-20): these were LazyRows with no visual
        // hint that they scroll - the last chip in each row ("Resolved" / the last category) sat
        // cut off right at the screen edge, indistinguishable from the row simply ending there.
        // Both are small, fixed-size sets (4 statuses, 5 categories, never open-ended), so
        // wrapping onto a second line via FlowRow is simpler and more discoverable than adding a
        // scroll-hint affordance to a LazyRow - matches the same fix already applied to
        // CaptureScreen's CategorySection and the Map legend.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip(
                label = translatedStringResource(R.string.report_list_all_statuses),
                selected = filter.statusFilter == null,
                onClick = { onFilterChange(filter.copy(statusFilter = null)) },
            )
            ReportStatus.entries.forEach { status ->
                CategoryChip(
                    label = status.label(),
                    selected = filter.statusFilter == status,
                    onClick = {
                        onFilterChange(filter.copy(statusFilter = if (filter.statusFilter == status) null else status))
                    },
                )
            }
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            CategoryChip(
                label = translatedStringResource(R.string.report_list_all_categories),
                selected = filter.categoryFilter == null,
                onClick = { onFilterChange(filter.copy(categoryFilter = null)) },
            )
            ReportCategory.entries.forEach { category ->
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
                        Text(
                            if (filter.sortOrder == ReportSortOrder.NEWEST_FIRST) {
                                translatedStringResource(R.string.report_list_sort_newest_first)
                            } else {
                                translatedStringResource(R.string.report_list_sort_oldest_first)
                            },
                        )
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

/**
 * The report's own captured photo, shown as a small rounded-square thumbnail in the list row -
 * matching the reference mockup's per-report photo tile, replacing what used to be a generic
 * category icon there (a wrench/lightbulb/etc symbol, not a photo of the actual issue). Falls
 * back to the category icon when there's no local photo to show: a video-only report (no
 * localPhotoPath at all - see Report.mediaType), an anonymous/offline-synced report whose local
 * file has since been cleared from device storage, or a load failure. localPhotoPath is a plain
 * device-local absolute file path (this project has no Firebase Storage/CDN - see
 * PLACEHOLDERS_TODO.csv), so Coil needs a java.io.File model rather than a URL string.
 */
@Composable
private fun ReportThumbnail(report: Report, modifier: Modifier = Modifier) {
    val photoPath = report.localPhotoPath
    if (report.mediaType == ReportMediaType.PHOTO && !photoPath.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(java.io.File(photoPath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.small),
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                imageVector = if (report.mediaType == ReportMediaType.VIDEO) Icons.Filled.Videocam else report.category.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun ReportListItem(
    report: Report,
    modifier: Modifier = Modifier,
    // Only ever true from an admin-facing screen (ui/admin/*) - a citizen's own report list
    // never needs to be told who filed a report they already know is theirs. Gates showing
    // report.reporterName so admins can see who reported the issue without exposing that same
    // identity on a citizen-facing list.
    isAdmin: Boolean = false,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                ReportThumbnail(
                    report = report,
                    modifier = Modifier
                        .size(56.dp)
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
                    // Blank for an anonymous report (no owner identity exists to show at all -
                    // see Report.isAnonymous) or for a report submitted before reporterName
                    // existed - falls through to nothing rendered rather than an empty line.
                    if (isAdmin && !report.isAnonymous && report.reporterName.isNotBlank()) {
                        Text(
                            text = translatedStringResource(R.string.report_list_reported_by, report.reporterName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        StatusBadge(status = report.status)
                        if (report.mediaType == ReportMediaType.VIDEO) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = translatedStringResource(R.string.report_list_video_report_description),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).padding(start = 8.dp),
                            )
                        }
                        if (report.isAnonymous) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = translatedStringResource(R.string.report_list_anonymous_report_description),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).padding(start = 8.dp),
                            )
                        }
                    }
                    // BUG FIX (2026-11-20, user-reported "I never know which report needs review
                    // unless I already got the notification and opened it"): a 16dp Warning icon
                    // sitting among other small status icons (video/anonymous) was the ONLY signal
                    // this report needed attention - easy to miss entirely while scrolling, and it
                    // never said what the AI actually suggested, only that *something* was flagged.
                    // This replaces it with a labeled, high-contrast banner naming the AI's
                    // suggested category directly on the card, so it's visible without opening the
                    // report or having seen the original notification.
                    // BUG FIX (2026-11-21, user-reported "if the reports had been resolved they
                    // don't need AI suggestion"): a resolved report can still carry
                    // needsAiReview=true if it was resolved before the citizen ever responded to
                    // the flag - the category is no longer actionable once the underlying issue is
                    // fixed, so this banner is suppressed for any RESOLVED report regardless of
                    // which list/tab is rendering it.
                    if (report.needsAiReview && report.status != ReportStatus.RESOLVED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val suggested = report.aiSuggestedCategory?.let {
                                if (it == ReportCategory.CUSTOM && report.aiSuggestedCustomLabel.isNotBlank()) {
                                    report.aiSuggestedCustomLabel
                                } else {
                                    it.displayLabel()
                                }
                            }
                            Text(
                                text = if (suggested != null) {
                                    translatedStringResource(R.string.report_list_ai_suggests_category, suggested)
                                } else {
                                    translatedStringResource(R.string.report_list_needs_input_description)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            trailingContent?.invoke()
        }
    }
}
