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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.components.CategoryChip
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.components.PrimaryActionButton
import com.king.mob_app_assignment.ui.permissions.PermissionExplainerScreen
import com.king.mob_app_assignment.util.AppPermission
import com.king.mob_app_assignment.util.PermissionManager
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

    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingFile
        val uri = pendingUri
        if (success && file != null && uri != null) {
            viewModel.onPhotoCaptured(uri, file)
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
            CapturePermissionStep.READY -> if (pendingUri == null) launchCamera()
            else -> Unit
        }
    }

    LaunchedEffect(uiState.stage) {
        if (uiState.stage == CaptureStage.SUBMITTED) {
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
            AppTopBar(title = "Report an issue")
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState.stage) {
                CaptureStage.IDLE -> Unit
                CaptureStage.PREVIEW -> PreviewStep(
                    photoUri = uiState.photoUri,
                    onRetake = {
                        viewModel.onRetake()
                        launchCamera()
                    },
                    onConfirm = viewModel::onConfirmCapture,
                )
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
private fun PreviewStep(photoUri: Uri?, onRetake: () -> Unit, onConfirm: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        AsyncImage(
            model = photoUri,
            contentDescription = "Captured photo",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium),
        )
        Text(
            text = "Review your photo",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormStep(uiState: CaptureUiState, viewModel: CaptureViewModel) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AsyncImage(
            model = uiState.photoUri,
            contentDescription = "Captured photo",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium),
        )

        Text(
            text = "Category",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        if (uiState.suggestedCategory != null && uiState.suggestionConfidence < 0.5f) {
            Text(
                text = "Low-confidence suggestion (${(uiState.suggestionConfidence * 100).toInt()}%) - please pick manually",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportCategory.entries.toList()) { category ->
                CategoryChip(
                    label = category.name,
                    selected = uiState.selectedCategory == category,
                    onClick = { viewModel.onCategorySelected(category) },
                )
            }
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
