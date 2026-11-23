package com.king.mob_app_assignment.ui.reports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportMediaType
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.reportReferenceCode
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.InfoCard
import com.king.mob_app_assignment.ui.components.InfoCardSeverity
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.components.StatusBadge
import com.king.mob_app_assignment.ui.components.VideoPlayer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.king.mob_app_assignment.ui.theme.translatedStringResource

private fun createResolutionPhotoFile(context: Context): File {
    val dir = File(context.getExternalFilesDir(null), "resolution_photos").apply { mkdirs() }
    return File(dir, "resolution_${System.currentTimeMillis()}.jpg")
}

private fun resolutionPhotoUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

@Composable
private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> translatedStringResource(R.string.report_detail_status_submitted)
    ReportStatus.IN_REVIEW -> translatedStringResource(R.string.report_detail_status_in_review)
    ReportStatus.RESOLVED -> translatedStringResource(R.string.report_detail_status_resolved)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    onBackClick: () -> Unit = {},
    viewModel: ReportDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Which media (if any) is currently shown full-size in FullMediaDialog - null means the
    // dialog is closed. Holds a model (the same value AsyncImage/VideoPlayer already take, a
    // Uri or a String path) plus whether it's a video, since this screen has two different
    // pieces of media that can be expanded (the report's own capture, and an admin's resolution
    // photo) and both need to open the same dialog.
    var expandedMedia by remember { mutableStateOf<Pair<Any, Boolean>?>(null) }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBackClick()
    }

    val canEditOrDelete = uiState.isOwner && uiState.report?.status == ReportStatus.SUBMITTED

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(translatedStringResource(R.string.report_detail_delete_dialog_title)) },
            text = { Text(translatedStringResource(R.string.report_detail_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteReport()
                }) { Text(translatedStringResource(R.string.report_detail_delete_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(translatedStringResource(R.string.report_detail_delete_dialog_cancel)) }
            },
        )
    }

    if (uiState.deleteErrorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteError,
            title = { Text(translatedStringResource(R.string.report_detail_delete_error_title)) },
            text = { Text(uiState.deleteErrorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDeleteError) { Text(translatedStringResource(R.string.report_detail_delete_error_ok)) }
            },
        )
    }

    val editContentDescription = translatedStringResource(R.string.report_detail_edit_action)
    val deleteContentDescription = translatedStringResource(R.string.report_detail_delete_action)

    Scaffold(
        topBar = {
            AppTopBar(
                title = translatedStringResource(R.string.report_detail_title),
                onBackClick = onBackClick,
                actions = {
                    if (canEditOrDelete && !uiState.isEditing) {
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(Icons.Filled.Edit, contentDescription = editContentDescription)
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = deleteContentDescription)
                        }
                    }
                },
            )
        },
    ) { padding ->
        val report = uiState.report
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoadingIndicator()
            }
        } else if (report == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(translatedStringResource(R.string.report_detail_not_found), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = reportReferenceCode(report.id),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (report.mediaType == ReportMediaType.VIDEO) {
                    // videoUrl (a real http(s) URL, were Firebase Storage ever added - see
                    // PLACEHOLDERS_TODO.csv) can go through Uri.parse directly; localVideoPath is
                    // a raw filesystem path and needs Uri.fromFile so ExoPlayer resolves it.
                    val videoUri = report.videoUrl?.let(Uri::parse)
                        ?: report.localVideoPath?.let { File(it) }?.let(Uri::fromFile)
                    if (videoUri != null) {
                        ReportMediaThumbnail(
                            model = videoUri,
                            isVideo = true,
                            onClick = { expandedMedia = videoUri to true },
                        )
                    }
                } else {
                    val photoModel = report.photoUrl ?: report.localPhotoPath
                    if (photoModel != null) {
                        ReportMediaThumbnail(
                            model = photoModel,
                            isVideo = false,
                            onClick = { expandedMedia = photoModel to false },
                        )
                    }
                }

                if (report.isAnonymous) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = translatedStringResource(R.string.report_detail_anonymous_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                // Admin-hidden: this card and its actions are meaningful only to the citizen who
                // owns the report (they're the one being asked to confirm/correct a category) -
                // an admin viewing the same report has no use for it.
                //
                // BUG FIX (2026-11-21, user-reported "if the report has been resolved they don't
                // need AI suggestion"): a resolved report can still have needsAiReview=true if it
                // was flagged and then resolved before the citizen ever responded (an admin can
                // resolve a report regardless of this flag - the two are independent). Once a
                // report is RESOLVED the category is moot for the citizen's purposes - the issue
                // is already fixed - so this card has no useful action left to offer and is
                // suppressed outright rather than asking the user to categorize an already-closed
                // report.
                if (report.needsAiReview && report.status != ReportStatus.RESOLVED && !uiState.isEditing && !uiState.isAdmin) {
                    Column {
                        // BUG FIX (2026-11-20, user feedback: "when I open it I want an option to
                        // accept the AI suggestion or go with my own" - this card used to always
                        // say "the AI couldn't categorize it, please choose yourself" and offer
                        // only one path out (a full edit form), even when aiSuggestedCategory WAS
                        // set and the citizen just wanted to agree with it in one tap, or
                        // explicitly keep their own pick. Now shows the AI's actual suggestion
                        // when one exists (consistent with the same suggestion already being shown
                        // in the My Reports list banner - see ReportListComponents.kt), with a
                        // direct accept/keep choice; falls back to the old low-confidence wording
                        // only when the AI genuinely couldn't produce a suggestion at all.
                        val suggestedLabel = report.aiSuggestedCategory?.let {
                            if (it == ReportCategory.CUSTOM && report.aiSuggestedCustomLabel.isNotBlank()) {
                                report.aiSuggestedCustomLabel
                            } else {
                                it.displayLabel()
                            }
                        }
                        InfoCard(
                            title = translatedStringResource(R.string.report_detail_ai_review_title),
                            message = if (suggestedLabel != null) {
                                translatedStringResource(R.string.report_detail_ai_review_message_with_suggestion, suggestedLabel)
                            } else {
                                translatedStringResource(R.string.report_detail_ai_review_message)
                            },
                            icon = Icons.Filled.WarningAmber,
                            severity = InfoCardSeverity.Warning,
                        )
                        if (uiState.isOwner) {
                            if (report.aiSuggestedCategory != null) {
                                PrimaryActionButton(
                                    text = translatedStringResource(R.string.report_detail_accept_ai_suggestion, suggestedLabel.orEmpty()),
                                    onClick = viewModel::acceptAiSuggestion,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                                )
                                OutlinedButton(
                                    onClick = viewModel::keepMyCategory,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                ) {
                                    Text(translatedStringResource(R.string.report_detail_keep_my_category, report.categoryDisplayText()))
                                }
                                TextButton(
                                    onClick = viewModel::startEditing,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                                ) {
                                    Text(translatedStringResource(R.string.report_detail_edit_manually))
                                }
                            } else {
                                PrimaryActionButton(
                                    text = translatedStringResource(R.string.report_detail_review_and_resubmit),
                                    onClick = viewModel::startEditing,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }

                if (uiState.isEditing) {
                    EditReportSection(
                        initialCategory = report.category,
                        initialNote = report.note,
                        initialCustomLabel = report.customLabel,
                        isSaving = uiState.isSaving,
                        onSave = viewModel::saveEdit,
                        onCancel = viewModel::cancelEditing,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = report.category.icon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = report.categoryDisplayText(),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (!uiState.isAdmin) {
                            StatusBadge(status = report.status)
                        }
                    }
                }

                // Blank for an anonymous report or one submitted before reporterName existed -
                // see ReportListItem's identical gate for why this simply renders nothing rather
                // than an empty "Reported by" line in either case.
                if (uiState.isAdmin && !report.isAnonymous && report.reporterName.isNotBlank()) {
                    Text(
                        text = translatedStringResource(R.string.report_list_reported_by, report.reporterName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (uiState.isAdmin) {
                    AdminWorkflowSection(
                        currentDepartment = report.department,
                        currentPriority = report.priority,
                        currentStatus = report.status,
                        isUpdatingStatus = uiState.isUpdatingStatus,
                        onDepartmentSelected = viewModel::assignDepartment,
                        onPrioritySelected = viewModel::setPriority,
                        onStatusUpdated = viewModel::updateStatusWithNote,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailRow(
                            icon = Icons.Filled.LocationOn,
                            label = translatedStringResource(R.string.report_detail_location_label),
                            value = report.address.ifBlank { translatedStringResource(R.string.report_detail_address_unavailable) },
                            secondary = "${report.latitude}, ${report.longitude}",
                        )
                        DetailRow(
                            icon = Icons.Filled.Explore,
                            label = translatedStringResource(R.string.report_detail_heading_label),
                            value = "${report.headingDegrees.toInt()}° ${report.headingCardinal}",
                        )
                        DetailRow(
                            icon = Icons.Filled.Schedule,
                            label = translatedStringResource(R.string.report_detail_captured_label),
                            value = dateFormat.format(Date(report.timestamp)),
                        )
                        if (!uiState.isEditing && report.note.isNotBlank()) {
                            DetailRow(
                                icon = Icons.Filled.Notes,
                                label = translatedStringResource(R.string.report_detail_note_label),
                                value = report.note,
                            )
                        }
                    }
                }

                if (!uiState.isEditing) {
                    StatusTimelineCard(
                        history = report.statusHistory,
                        fallbackStatus = report.status,
                        fallbackTimestamp = report.timestamp,
                    )
                }

                uiState.incident?.let { incident ->
                    IncidentSummaryCard(incident = incident)
                }

                if (!uiState.isAdmin && !uiState.isEditing) {
                    CitizenTriageRow(
                        department = report.department,
                        priority = report.priority,
                    )
                }

                if (!uiState.isAdmin && !uiState.isEditing && report.resolutionNote.isNotBlank()) {
                    Column {
                        InfoCard(
                            title = translatedStringResource(R.string.report_detail_resolution_update_title),
                            message = report.resolutionNote,
                            icon = Icons.Filled.CheckCircle,
                            severity = InfoCardSeverity.Positive,
                        )
                        val resolutionPhotoPath = report.resolutionPhotoPath
                        if (!resolutionPhotoPath.isNullOrBlank()) {
                            ReportMediaThumbnail(
                                model = resolutionPhotoPath,
                                isVideo = false,
                                contentDescription = translatedStringResource(R.string.report_detail_resolution_photo_description),
                                onClick = { expandedMedia = resolutionPhotoPath to false },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }

            expandedMedia?.let { (model, isVideo) ->
                FullMediaDialog(model = model, isVideo = isVideo, onDismiss = { expandedMedia = null })
            }
        }
    }
}

/**
 * Small tappable preview of a report's photo/video (or an admin's resolution photo) - replaces
 * what used to be a full-width 3:4 image/video permanently occupying a large share of the
 * screen, the main reason this page needed to scroll as far as it did. Tapping it opens
 * [FullMediaDialog]. Mirrors CaptureScreen.kt's CaptureThumbnail/FullMediaDialog pattern so both
 * screens present captured media the same way.
 */
@Composable
private fun ReportMediaThumbnail(
    model: Any,
    isVideo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resolvedContentDescription = contentDescription ?: translatedStringResource(R.string.report_detail_media_photo_description)
    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(elevation = com.king.mob_app_assignment.ui.theme.Elevation.raised, shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        if (isVideo) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = resolvedContentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Full-screen on-demand view of a report's media, opened from [ReportMediaThumbnail]. */
@Composable
private fun FullMediaDialog(model: Any, isVideo: Boolean, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (isVideo && model is Uri) {
                VideoPlayer(uri = model, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = translatedStringResource(R.string.report_detail_full_media_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = translatedStringResource(R.string.report_detail_close_action), tint = Color.White)
            }
        }
    }
}

@Composable
private fun CitizenTriageRow(
    department: Department?,
    priority: ReportPriority,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DepartmentLabel(department = department)
            Spacer(modifier = Modifier.width(12.dp))
            PriorityIndicator(priority = priority)
        }
    }
}

@Composable
private fun EditReportSection(
    initialCategory: ReportCategory,
    initialNote: String,
    initialCustomLabel: String,
    isSaving: Boolean,
    onSave: (ReportCategory, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var note by remember { mutableStateOf(initialNote) }
    var customLabel by remember { mutableStateOf(initialCustomLabel) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = translatedStringResource(R.string.report_detail_edit_heading), style = MaterialTheme.typography.headlineSmall)

        // BUG FIX (found via manual testing, 2026-11-20): a LazyRow here clipped the last
        // category chip off the edge of the screen with no scroll hint - same pattern already
        // fixed in CaptureScreen's CategorySection, the Map legend, and the admin report
        // filters. ReportCategory.selectableEntries is a small, fixed set, so FlowRow wrapping
        // is simpler and more discoverable than a scroll affordance.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportCategory.selectableEntries.forEach { category ->
                CategoryChip(
                    label = category.displayLabel(),
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                )
            }
        }

        if (selectedCategory == ReportCategory.CUSTOM) {
            OutlinedTextField(
                value = customLabel,
                onValueChange = { if (it.length <= 80) customLabel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(translatedStringResource(R.string.report_detail_describe_issue_label)) },
                placeholder = { Text(translatedStringResource(R.string.report_detail_describe_issue_placeholder)) },
                supportingText = { Text(translatedStringResource(R.string.report_detail_char_count, customLabel.length, 80)) },
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 500) note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(translatedStringResource(R.string.report_detail_note_label)) },
            supportingText = { Text(translatedStringResource(R.string.report_detail_char_count, note.length, 500)) },
        )

        val canSave = selectedCategory != ReportCategory.CUSTOM || customLabel.isNotBlank()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                Text(translatedStringResource(R.string.report_detail_cancel))
            }
            PrimaryActionButton(
                text = translatedStringResource(R.string.report_detail_save),
                onClick = { onSave(selectedCategory, note, customLabel.trim()) },
                enabled = canSave,
                isLoading = isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AdminWorkflowSection(
    currentDepartment: Department?,
    currentPriority: ReportPriority,
    currentStatus: ReportStatus,
    isUpdatingStatus: Boolean,
    onDepartmentSelected: (Department?) -> Unit,
    onPrioritySelected: (ReportPriority) -> Unit,
    onStatusUpdated: (ReportStatus, String, File?) -> Unit,
) {
    val context = LocalContext.current
    var selectedStatusForChange by remember(currentStatus) { mutableStateOf<ReportStatus?>(null) }
    var resolutionNote by remember { mutableStateOf("") }
    var resolutionPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) resolutionPhotoFile = pendingCameraFile
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = com.king.mob_app_assignment.ui.theme.Elevation.resting),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = translatedStringResource(R.string.report_detail_admin_workflow_title), style = MaterialTheme.typography.titleMedium)

            Text(text = translatedStringResource(R.string.report_detail_department_label), style = MaterialTheme.typography.labelLarge)
            // BUG FIX (found via manual testing, 2026-11-20): same LazyRow clipping pattern as
            // above - Department.entries is a small, fixed set (Roads/Sanitation/Electrical/
            // Water/Other), so FlowRow wrapping beats a scroll affordance here too.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Department.entries.forEach { department ->
                    CategoryChip(
                        label = department.displayLabel(),
                        selected = currentDepartment == department,
                        onClick = {
                            onDepartmentSelected(if (currentDepartment == department) null else department)
                        },
                    )
                }
            }

            Text(text = translatedStringResource(R.string.report_detail_priority_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = currentPriority == priority,
                        onClick = { onPrioritySelected(priority) },
                        label = { Text(priority.displayLabel()) },
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = priority.dotColor(MaterialTheme.colorScheme),
                            selectedContainerColor = priority.dotColor(MaterialTheme.colorScheme).copy(alpha = 0.18f),
                            selectedLabelColor = priority.dotColor(MaterialTheme.colorScheme),
                        ),
                    )
                }
            }

            Text(text = translatedStringResource(R.string.report_detail_status_label), style = MaterialTheme.typography.labelLarge)
            if (selectedStatusForChange == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportStatus.entries.forEach { status ->
                        CategoryChip(
                            label = status.label(),
                            selected = currentStatus == status,
                            onClick = {
                                if (status != currentStatus) {
                                    if (status == ReportStatus.IN_REVIEW || status == ReportStatus.RESOLVED) {
                                        resolutionNote = ""
                                        resolutionPhotoFile = null
                                        selectedStatusForChange = status
                                    } else {
                                        onStatusUpdated(status, "", null)
                                    }
                                }
                            },
                        )
                    }
                }
            } else {
                val targetStatus = selectedStatusForChange!!
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = translatedStringResource(R.string.report_detail_status_change_prompt, targetStatus.label()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = resolutionNote,
                        onValueChange = { if (it.length <= 500) resolutionNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(translatedStringResource(R.string.report_detail_resolution_note_label)) },
                        supportingText = { Text(translatedStringResource(R.string.report_detail_char_count, resolutionNote.length, 500)) },
                        minLines = 3,
                    )
                    if (targetStatus == ReportStatus.RESOLVED) {
                        if (resolutionPhotoFile != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(translatedStringResource(R.string.report_detail_resolution_photo_attached), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val file = createResolutionPhotoFile(context)
                                    val uri = resolutionPhotoUriFor(context, file)
                                    pendingCameraFile = file
                                    pendingCameraUri = uri
                                    takePictureLauncher.launch(uri)
                                },
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Text(text = translatedStringResource(R.string.report_detail_attach_photo_optional), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { selectedStatusForChange = null },
                            enabled = !isUpdatingStatus,
                            modifier = Modifier.weight(1f),
                        ) { Text(translatedStringResource(R.string.report_detail_cancel)) }
                        PrimaryActionButton(
                            text = translatedStringResource(R.string.report_detail_update_status),
                            onClick = {
                                onStatusUpdated(targetStatus, resolutionNote.trim(), resolutionPhotoFile)
                                selectedStatusForChange = null
                            },
                            enabled = resolutionNote.isNotBlank(),
                            isLoading = isUpdatingStatus,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    secondary: String? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 12.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Citizen-facing incident visibility, deliberately kept simple and free of admin jargon (no
// "confidence score," no member-management controls, no severity/department editing - those
// stay admin-only). The brief's own requirement is "show incident information to citizens where
// appropriate" - this is that: a citizen sees their report isn't isolated, without needing to
// understand incidents, work orders, or risk scoring as concepts at all.
@Composable
private fun IncidentSummaryCard(incident: com.king.mob_app_assignment.domain.model.Incident) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = translatedStringResource(R.string.report_detail_incident_summary_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = translatedStringResource(R.string.report_detail_incident_summary_message, incident.reportCount, incident.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
