package com.king.mob_app_assignment.ui.reports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.king.mob_app_assignment.domain.model.Department
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportMediaType
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.domain.model.displayLabel
import com.king.mob_app_assignment.domain.model.reportReferenceCode
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.components.StatusBadge
import com.king.mob_app_assignment.ui.components.VideoPlayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun createResolutionPhotoFile(context: Context): File {
    val dir = File(context.getExternalFilesDir(null), "resolution_photos").apply { mkdirs() }
    return File(dir, "resolution_${System.currentTimeMillis()}.jpg")
}

private fun resolutionPhotoUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun ReportStatus.label(): String = when (this) {
    ReportStatus.SUBMITTED -> "Submitted"
    ReportStatus.IN_REVIEW -> "In review"
    ReportStatus.RESOLVED -> "Resolved"
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

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBackClick()
    }

    val canEditOrDelete = uiState.isOwner && uiState.report?.status == ReportStatus.SUBMITTED

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this report?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteReport()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (uiState.deleteErrorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteError,
            title = { Text("Couldn't delete report") },
            text = { Text(uiState.deleteErrorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDeleteError) { Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Report detail",
                onBackClick = onBackClick,
                actions = {
                    if (canEditOrDelete && !uiState.isEditing) {
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit report")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete report")
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
                Text("Report not found", style = MaterialTheme.typography.titleMedium)
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
                        VideoPlayer(
                            uri = videoUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(MaterialTheme.shapes.medium),
                        )
                    }
                } else {
                    AsyncImage(
                        model = report.photoUrl ?: report.localPhotoPath,
                        contentDescription = "Report photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
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
                            text = "Submitted anonymously",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                if (report.needsAiReview && !uiState.isEditing) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "AI review needed",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            val suggestionText = report.aiSuggestedCategory?.let { suggested ->
                                val suggestedText = if (suggested == ReportCategory.CUSTOM && report.aiSuggestedCustomLabel.isNotBlank()) {
                                    report.aiSuggestedCustomLabel
                                } else {
                                    suggested.displayLabel()
                                }
                                "Our AI suggests this might be \"$suggestedText\" instead of \"${report.categoryDisplayText()}\"."
                            } ?: "Our AI couldn't confidently classify this photo."
                            Text(
                                text = "$suggestionText Please review the category and add a note, then resubmit.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (uiState.isOwner) {
                                PrimaryActionButton(
                                    text = "Review and resubmit",
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailRow(
                            icon = Icons.Filled.LocationOn,
                            label = "Location",
                            value = report.address.ifBlank { "Address unavailable" },
                            secondary = "${report.latitude}, ${report.longitude}",
                        )
                        DetailRow(
                            icon = Icons.Filled.Explore,
                            label = "Heading",
                            value = "${report.headingDegrees.toInt()}° ${report.headingCardinal}",
                        )
                        DetailRow(
                            icon = Icons.Filled.Schedule,
                            label = "Captured",
                            value = dateFormat.format(Date(report.timestamp)),
                        )
                        if (!uiState.isEditing && report.note.isNotBlank()) {
                            DetailRow(
                                icon = Icons.Filled.Notes,
                                label = "Note",
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

                if (!uiState.isAdmin && !uiState.isEditing) {
                    CitizenTriageAndUpvoteRow(
                        department = report.department,
                        priority = report.priority,
                        upvoteCount = report.upvoteCount,
                        isUpvotedByMe = uiState.currentUid != null && uiState.currentUid in report.upvotedByUids,
                        canUpvote = !uiState.isOwner && uiState.currentUid != null,
                        onUpvoteClick = viewModel::toggleUpvote,
                    )
                }

                if (!uiState.isAdmin && !uiState.isEditing && report.resolutionNote.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "Update from the city",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Text(
                                text = report.resolutionNote,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (!report.resolutionPhotoPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = report.resolutionPhotoPath,
                                    contentDescription = "Resolution photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CitizenTriageAndUpvoteRow(
    department: Department?,
    priority: ReportPriority,
    upvoteCount: Int,
    isUpvotedByMe: Boolean,
    canUpvote: Boolean,
    onUpvoteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DepartmentLabel(department = department)
                Spacer(modifier = Modifier.width(12.dp))
                PriorityIndicator(priority = priority)
            }
            UpvoteButton(
                upvoteCount = upvoteCount,
                isUpvotedByMe = isUpvotedByMe,
                canUpvote = canUpvote,
                onClick = onUpvoteClick,
            )
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
        Text(text = "Edit report", style = MaterialTheme.typography.headlineSmall)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportCategory.selectableEntries) { category ->
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
                label = { Text("Describe the issue") },
                placeholder = { Text("e.g. Fallen tree blocking road") },
                supportingText = { Text("${customLabel.length}/80") },
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 500) note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            supportingText = { Text("${note.length}/500") },
        )

        val canSave = selectedCategory != ReportCategory.CUSTOM || customLabel.isNotBlank()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            PrimaryActionButton(
                text = "Save",
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Admin workflow", style = MaterialTheme.typography.titleMedium)

            Text(text = "Department", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Department.entries.toList()) { department ->
                    CategoryChip(
                        label = department.displayLabel(),
                        selected = currentDepartment == department,
                        onClick = {
                            onDepartmentSelected(if (currentDepartment == department) null else department)
                        },
                    )
                }
            }

            Text(text = "Priority", style = MaterialTheme.typography.labelLarge)
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

            Text(text = "Status", style = MaterialTheme.typography.labelLarge)
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
                        text = "Moving to \"${targetStatus.label()}\" - add a resolution note for the citizen:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = resolutionNote,
                        onValueChange = { if (it.length <= 500) resolutionNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Resolution note") },
                        supportingText = { Text("${resolutionNote.length}/500") },
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
                                Text("Resolution photo attached", style = MaterialTheme.typography.bodyMedium)
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
                                Text(text = "Attach photo (optional)", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { selectedStatusForChange = null },
                            enabled = !isUpdatingStatus,
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel") }
                        PrimaryActionButton(
                            text = "Update status",
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
