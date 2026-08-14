package com.king.mob_app_assignment.ui.capture

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.components.VideoPlayer
import com.king.mob_app_assignment.ui.permissions.PermissionExplainerScreen
import com.king.mob_app_assignment.util.AppPermission
import com.king.mob_app_assignment.util.NormalizedStroke
import com.king.mob_app_assignment.util.PermissionManager
import com.king.mob_app_assignment.util.PhotoAnnotator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CapturePermissionStep { CAMERA_EXPLAINER, CAMERA_REQUEST, LOCATION_EXPLAINER, LOCATION_REQUEST, READY }

private fun createPhotoFile(context: Context): File {
    val dir = File(context.getExternalFilesDir(null), "report_photos").apply { mkdirs() }
    return File(dir, "report_${System.currentTimeMillis()}.jpg")
}

private fun photoUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun createVideoFile(context: Context): File {
    val dir = File(context.getExternalFilesDir(null), "report_videos").apply { mkdirs() }
    return File(dir, "report_${System.currentTimeMillis()}.mp4")
}

// Gallery video picks are copied into our own app-private file for the same reason
// copyGalleryImageToReportFile copies photos - the picked content:// Uri isn't a File we control,
// and the rest of the pipeline (report submission, offline queueing, admin/detail playback)
// expects a real local file path.
private fun copyGalleryVideoToReportFile(context: Context, sourceUri: Uri): File? {
    val destination = createVideoFile(context)
    return runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        destination
    }.getOrNull()
}

// Gallery picks hand back a content:// Uri into the media provider, not a File we control - and
// the rest of the capture pipeline (EXIF stripping, local-only photo storage, admin resolution
// photos) all operate on a real File in our own app-private directory. Copying the picked image
// into that same directory means the gallery path can reuse the exact same PREVIEW/ANALYZING/FORM
// flow as a camera capture with zero changes to CaptureViewModel.
private fun copyGalleryImageToReportFile(context: Context, sourceUri: Uri): File? {
    val destination = createPhotoFile(context)
    return runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        destination
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
    onViewExistingReport: (String) -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoFile by remember { mutableStateOf<File?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Cancelling the camera intent (back button, etc) used to dismiss the whole capture screen
    // immediately - now it drops back to a small chooser (camera icon + gallery icon) instead,
    // so backing out of the camera isn't a dead end.
    var cameraWasCancelled by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingFile
        val uri = pendingUri
        if (success && file != null && uri != null) {
            viewModel.onPhotoCaptured(uri, file)
        } else {
            cameraWasCancelled = true
        }
    }

    // Copies the picked gallery image into our own app-private file (see
    // copyGalleryImageToReportFile) so it can flow through the exact same PREVIEW/ANALYZING/FORM
    // pipeline as a camera photo. A null result (copy failed, e.g. picker cancelled or the source
    // was unreadable) dismisses the screen rather than leaving the user on a stuck loading state.
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { sourceUri ->
        if (sourceUri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        val file = copyGalleryImageToReportFile(context, sourceUri)
        if (file != null) {
            viewModel.onPhotoCaptured(photoUriFor(context, file), file)
        } else {
            onDismiss()
        }
    }

    val takeVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val file = pendingVideoFile
        val uri = pendingVideoUri
        if (success && file != null && uri != null) {
            viewModel.onVideoCaptured(uri, file)
        } else {
            cameraWasCancelled = true
        }
    }

    val galleryVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { sourceUri ->
        if (sourceUri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        val file = copyGalleryVideoToReportFile(context, sourceUri)
        if (file != null) {
            viewModel.onVideoCaptured(photoUriFor(context, file), file)
        } else {
            onDismiss()
        }
    }

    fun launchCamera() {
        val file = createPhotoFile(context)
        val uri = photoUriFor(context, file)
        pendingFile = file
        pendingUri = uri
        takePictureLauncher.launch(uri)
    }

    fun launchVideoCamera() {
        val file = createVideoFile(context)
        val uri = photoUriFor(context, file)
        pendingVideoFile = file
        pendingVideoUri = uri
        takeVideoLauncher.launch(uri)
    }

    fun launchGalleryPicker() {
        galleryPickerLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }

    fun launchGalleryVideoPicker() {
        galleryVideoPickerLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.VideoOnly,
            ),
        )
    }

    var permissionStep by remember {
        mutableStateOf(
            if (PermissionManager.isGranted(context, AppPermission.CAMERA)) {
                if (PermissionManager.isGranted(context, AppPermission.LOCATION)) {
                    CapturePermissionStep.READY
                } else {
                    CapturePermissionStep.LOCATION_EXPLAINER
                }
            } else {
                CapturePermissionStep.CAMERA_EXPLAINER
            },
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        permissionStep = if (PermissionManager.isGranted(context, AppPermission.LOCATION)) {
            CapturePermissionStep.READY
        } else {
            CapturePermissionStep.LOCATION_EXPLAINER
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        permissionStep = CapturePermissionStep.READY
    }

    LaunchedEffect(permissionStep) {
        when (permissionStep) {
            CapturePermissionStep.CAMERA_REQUEST ->
                cameraPermissionLauncher.launch(AppPermission.CAMERA.manifestPermission)
            CapturePermissionStep.LOCATION_REQUEST ->
                locationPermissionLauncher.launch(AppPermission.LOCATION.manifestPermission)
            CapturePermissionStep.READY -> if (pendingUri == null && !cameraWasCancelled) launchCamera()
            else -> Unit
        }
    }

    LaunchedEffect(uiState.stage) {
        if (uiState.stage == CaptureStage.SUBMITTED) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onSubmitted()
        }
    }

    if (permissionStep == CapturePermissionStep.CAMERA_EXPLAINER) {
        PermissionExplainerScreen(
            icon = Icons.Filled.PhotoCamera,
            title = "Camera access needed",
            body = "Shake to Report uses your camera to capture a photo of the issue you're reporting.",
            onContinue = { permissionStep = CapturePermissionStep.CAMERA_REQUEST },
        )
        return
    }
    if (permissionStep == CapturePermissionStep.LOCATION_EXPLAINER) {
        PermissionExplainerScreen(
            icon = Icons.Filled.LocationOn,
            title = "Location access needed",
            body = "Shake to Report tags each report with its GPS location so the issue can be found and resolved.",
            onContinue = { permissionStep = CapturePermissionStep.LOCATION_REQUEST },
        )
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "Report an issue", onBackClick = onDismiss)
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState.stage) {
                CaptureStage.IDLE -> if (cameraWasCancelled) {
                    CaptureChooserStep(
                        onTakePhoto = {
                            cameraWasCancelled = false
                            launchCamera()
                        },
                        onChooseFromGallery = ::launchGalleryPicker,
                        onRecordVideo = {
                            cameraWasCancelled = false
                            launchVideoCamera()
                        },
                        onChooseVideoFromGallery = ::launchGalleryVideoPicker,
                    )
                }
                CaptureStage.PREVIEW -> if (uiState.videoUri != null) {
                    VideoPreviewStep(
                        videoUri = uiState.videoUri,
                        onRetake = {
                            viewModel.onRetake()
                            launchVideoCamera()
                        },
                        onConfirm = viewModel::onConfirmCapture,
                    )
                } else {
                    PreviewStep(
                        photoUri = uiState.photoUri,
                        photoFile = uiState.photoFile,
                        onRetake = {
                            viewModel.onRetake()
                            launchCamera()
                        },
                        onConfirm = viewModel::onConfirmCapture,
                    )
                }
                CaptureStage.ANALYZING -> AnalyzingStep(
                    label = "Analyzing photo",
                    detail = "Detecting the issue category and reading your location...",
                )
                CaptureStage.FORM, CaptureStage.DUPLICATE_CHECK -> FormStep(uiState = uiState, viewModel = viewModel)
                CaptureStage.SUBMITTING -> AnalyzingStep(
                    label = "Submitting report",
                    detail = "Sending your report to the city - almost done.",
                )
                CaptureStage.SUBMITTED -> Unit
            }

            if (uiState.stage == CaptureStage.DUPLICATE_CHECK && uiState.duplicateCandidate != null) {
                val duplicate = uiState.duplicateCandidate
                AlertDialog(
                    onDismissRequest = viewModel::dismissDuplicateDialog,
                    title = { Text("Similar report nearby") },
                    text = { Text("A similar ${duplicate?.category?.name} report was filed nearby recently. View it instead, or submit anyway?") },
                    confirmButton = {
                        TextButton(onClick = viewModel::submitAnyway) { Text("Submit anyway") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            duplicate?.id?.let(onViewExistingReport)
                            viewModel.dismissDuplicateDialog()
                        }) { Text("View existing") }
                    },
                )
            }
        }
    }
}

@Composable
private fun CaptureChooserStep(
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRecordVideo: () -> Unit,
    onChooseVideoFromGallery: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = "No photo or video yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Take a photo or record a video of the issue, or pick one from your gallery.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryActionButton(
                text = "Take photo",
                onClick = onTakePhoto,
                icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            )
            IconButton(
                onClick = onChooseFromGallery,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "Choose from gallery",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            OutlinedButton(
                onClick = onRecordVideo,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null)
                Text(text = "Record video", modifier = Modifier.padding(start = 8.dp))
            }
            IconButton(
                onClick = onChooseVideoFromGallery,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = "Choose video from gallery",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // AI classification only ever runs on photos - video reports skip it entirely (see
        // CaptureViewModel.submit()) so the note sets expectations up front rather than the user
        // discovering the category field is suddenly required only once they reach the form.
        Text(
            text = "Note: video reports require you to choose a category yourself - AI can't classify video.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

// Video reports skip the freehand-annotation step entirely (drawing over a moving video makes no
// sense the way it does over a still photo) - this is a deliberately simpler preview than
// PreviewStep, just playback + retake/confirm.
@Composable
private fun VideoPreviewStep(
    videoUri: Uri?,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (videoUri != null) {
            VideoPlayer(
                uri = videoUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium),
            )
        }

        Text(
            text = "Review your video",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Retake if the issue isn't clearly visible, or confirm to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Retake", style = MaterialTheme.typography.labelLarge)
            }
            PrimaryActionButton(
                text = "Confirm",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val ANNOTATION_COLOR = Color(0xFFFF2D14)

@Composable
private fun PreviewStep(
    photoUri: Uri?,
    photoFile: File?,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Offset(0f, 0f)) }
    var isBaking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium),
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        canvasSize = Offset(size.width.toFloat(), size.height.toFloat())
                        detectDragGestures(
                            onDragStart = { offset -> currentStroke = listOf(offset) },
                            onDrag = { change, _ ->
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                if (currentStroke.size >= 2) strokes.add(currentStroke)
                                currentStroke = emptyList()
                            },
                        )
                    },
            ) {
                canvasSize = Offset(size.width, size.height)
                val strokeWidthPx = size.width * 0.012f
                (strokes + listOf(currentStroke)).forEach { points ->
                    if (points.size < 2) return@forEach
                    val path = Path()
                    path.moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { path.lineTo(it.x, it.y) }
                    drawPath(
                        path = path,
                        color = ANNOTATION_COLOR,
                        style = Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Draw to mark the issue (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                enabled = strokes.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last stroke")
            }
            IconButton(
                onClick = { strokes.clear() },
                enabled = strokes.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear annotation")
            }
        }

        Text(
            text = "Review your photo",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Retake if the issue isn't clearly visible, or confirm to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !isBaking,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Retake", style = MaterialTheme.typography.labelLarge)
            }
            PrimaryActionButton(
                text = "Confirm",
                isLoading = isBaking,
                onClick = onClick@{
                    if (strokes.isEmpty() || photoFile == null || canvasSize.x <= 0f || canvasSize.y <= 0f) {
                        onConfirm()
                        return@onClick
                    }
                    val normalized = strokes.map { points ->
                        NormalizedStroke(
                            points.map { it.x / canvasSize.x to it.y / canvasSize.y },
                        )
                    }
                    isBaking = true
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            PhotoAnnotator.bakeStrokesIntoFile(photoFile, normalized)
                        }
                        isBaking = false
                        onConfirm()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AnalyzingStep(label: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CaptureMetadataCard(
    address: String,
    latitude: Double,
    longitude: Double,
    headingDegrees: Float,
    headingCardinal: String,
    timestamp: Long,
    dateFormat: SimpleDateFormat,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetadataRow(
                icon = Icons.Filled.LocationOn,
                label = "Location",
                value = address.ifBlank { "Unavailable" } + "  ($latitude, $longitude)",
            )
            MetadataRow(
                icon = Icons.Filled.Explore,
                label = "Heading",
                value = "${headingDegrees.toInt()}° $headingCardinal",
            )
            MetadataRow(
                icon = Icons.Filled.Schedule,
                label = "Captured",
                value = dateFormat.format(Date(timestamp)),
            )
        }
    }
}

// Deliberately does NOT present category selection as an always-visible, seemingly-required
// chip row - user feedback was that seeing the picker there always made it read as compulsory.
// Default state instead reads as "AI will handle this" (matching how the rest of the AI pipeline
// already works: classification happens after submit, not on this screen) with a small text
// button to expand into manual picking only for users who want to override it up front.
@Composable
private fun CategorySection(
    selectedCategory: ReportCategory?,
    onCategorySelected: (ReportCategory) -> Unit,
    // Video reports have no AI fallback (see submit()'s manual-category requirement) - the
    // picker is always expanded and required for them, never collapsible into "AI will handle it."
    isRequired: Boolean = false,
) {
    var isExpanded by remember { mutableStateOf(isRequired || selectedCategory != null) }

    Text(
        text = "Category",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )

    if (!isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Our AI will detect the issue type automatically after you submit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { isExpanded = true }) {
                Text("Choose manually")
            }
        }
    } else {
        Text(
            text = if (isRequired) {
                "AI can't classify video - please choose the category yourself."
            } else {
                "AI will still double-check this after you submit - if it disagrees, you'll be asked to review it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportCategory.selectableEntries) { category ->
                CategoryChip(
                    label = category.name,
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormStep(uiState: CaptureUiState, viewModel: CaptureViewModel) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val videoUri = uiState.videoUri
    val isVideo = videoUri != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (videoUri != null) {
            VideoPlayer(
                uri = videoUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium),
            )
        } else {
            AsyncImage(
                model = uiState.photoUri,
                contentDescription = "Captured photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium),
            )
        }

        CategorySection(
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = viewModel::onCategorySelected,
            isRequired = isVideo,
        )

        if (uiState.selectedCategory == ReportCategory.CUSTOM) {
            OutlinedTextField(
                value = uiState.customLabel,
                onValueChange = viewModel::onCustomLabelChange,
                label = { Text("Describe the issue") },
                placeholder = { Text("e.g. Fallen tree blocking road") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                supportingText = { Text("${uiState.customLabel.length}/80") },
            )
        }

        OutlinedTextField(
            value = uiState.note,
            onValueChange = viewModel::onNoteChange,
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = MaterialTheme.shapes.small,
            supportingText = { Text("${uiState.note.length}/500") },
        )

        Text(
            text = "Report details",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        CaptureMetadataCard(
            address = uiState.address,
            latitude = uiState.latitude,
            longitude = uiState.longitude,
            headingDegrees = uiState.headingDegrees,
            headingCardinal = uiState.headingCardinal,
            timestamp = uiState.timestamp,
            dateFormat = dateFormat,
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = "Report anonymously", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Your identity won't be attached to this report at all - not even to us. " +
                                "You won't be able to edit or delete it afterward, since there's no owner on record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Switch(
                    checked = uiState.isAnonymous,
                    onCheckedChange = viewModel::onAnonymousToggle,
                )
            }
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryActionButton(
            text = "Submit",
            onClick = viewModel::submit,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
