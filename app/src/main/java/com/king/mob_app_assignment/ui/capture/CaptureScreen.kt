package com.king.mob_app_assignment.ui.capture

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.king.mob_app_assignment.ui.reports.displayLabel
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.king.mob_app_assignment.R
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
import com.king.mob_app_assignment.ui.theme.translatedStringResource

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
    viaShake: Boolean = false,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
    onViewExistingReport: (String) -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // Shake-to-report can launch straight into the camera over a locked screen (see
    // MainActivity.allowShowingOverLockScreenForShakeCapture) so the photo itself is never
    // missed. But nothing past that point - AI classification, editing the form, and especially
    // submit() (which touches the signed-in account and the network) - should be reachable while
    // the device is still locked. The instant a photo comes back (stage advances off IDLE), this
    // asks the OS's own KeyguardManager to prompt for the device's real PIN/pattern/biometric,
    // exactly as if the user had unlocked normally - never a screen this app builds itself.
    if (viaShake) {
        RequireDeviceUnlockOncePhotoCaptured(stage = uiState.stage)
    }

    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoFile by remember { mutableStateOf<File?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Separate pending slot from pendingFile/pendingUri above - those belong to the shake flow's
    // single PREVIEW-bound capture (see takePictureLauncher below), while this one backs the
    // form-first flow's "Add Photo" tile, which can be tapped repeatedly to add several photos
    // without ever leaving CaptureStage.FORM.
    var pendingFormPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingFormPhotoUri by remember { mutableStateOf<Uri?>(null) }

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
            // SECURITY FIX (2026-11-18, alongside the shake-bypasses-app-lock feature): a shake
            // that opens the camera but gets CANCELLED (back button, no photo taken) never
            // reaches RequireDeviceUnlockOncePhotoCaptured's markShakeCaptureFinished() call - that
            // only fires once a photo/video is actually captured. Without clearing the bypass
            // here too, cancelling the camera would leave the app-lock PIN bypassed indefinitely
            // for the rest of the session, a real gap in app-lock's whole purpose. Covers the
            // viaShake entry point only in effect, since this bypass is only ever set true for a
            // shake-triggered launch in the first place.
            com.king.mob_app_assignment.util.AppLockSessionState.markShakeCaptureFinished()
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

    // The form-first flow's own take-picture launcher/permission request - deliberately separate
    // from takePictureLauncher/launchCamera above, which are wired to reset state to a fresh
    // PREVIEW stage (the shake flow's single-photo-then-confirm model). This one instead appends
    // to the FORM stage's photo list via onFormPhotoAdded and never leaves CaptureStage.FORM.
    val formPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingFormPhotoFile
        val uri = pendingFormPhotoUri
        if (success && file != null && uri != null) {
            viewModel.onFormPhotoAdded(uri, file)
        }
    }

    fun launchFormPhotoCamera() {
        val file = createPhotoFile(context)
        val uri = photoUriFor(context, file)
        pendingFormPhotoFile = file
        pendingFormPhotoUri = uri
        formPhotoLauncher.launch(uri)
    }

    val formPhotoCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchFormPhotoCamera() }

    fun requestFormPhotoCapture() {
        if (PermissionManager.isGranted(context, AppPermission.CAMERA)) {
            launchFormPhotoCamera()
        } else {
            formPhotoCameraPermissionLauncher.launch(AppPermission.CAMERA.manifestPermission)
        }
    }

    // Shake-to-report needs a photo immediately (that's the entire point of the gesture), so it
    // still eagerly walks camera -> location permission -> auto-launch-camera exactly as before
    // multi-photo support existed. The form-first flow (viaShake == false - "Report Issue"
    // button, a notification tap, etc) instead lands straight on an empty form with no
    // permission prompt at all; camera permission there is only ever requested on-demand, right
    // when "Add Photo" is actually tapped (see FormPhotoLauncher below), and location is captured
    // in the background by startFormFirstFlow() without needing an explicit permission gate here
    // (LocationProvider already handles a denied/unavailable location gracefully - see its own
    // fallback-to-0.0/0.0 behavior, unchanged from before this flow existed).
    var permissionStep by remember {
        mutableStateOf(
            if (!viaShake) {
                CapturePermissionStep.READY
            } else if (PermissionManager.isGranted(context, AppPermission.CAMERA)) {
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
            CapturePermissionStep.READY ->
                if (viaShake && pendingUri == null && !cameraWasCancelled) launchCamera()
            else -> Unit
        }
    }

    // Kicks off the form-first flow exactly once on entry (Unit key, not viaShake, since viaShake
    // never changes for a given screen instance) - fetches location/heading in the background and
    // lands on an empty FORM stage with no photo yet, unlike shake's PREVIEW-first path above.
    LaunchedEffect(Unit) {
        if (!viaShake && uiState.stage == CaptureStage.IDLE) {
            viewModel.startFormFirstFlow()
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
            title = translatedStringResource(R.string.capture_camera_permission_title),
            body = translatedStringResource(R.string.capture_camera_permission_body),
            onContinue = { permissionStep = CapturePermissionStep.CAMERA_REQUEST },
        )
        return
    }
    if (permissionStep == CapturePermissionStep.LOCATION_EXPLAINER) {
        PermissionExplainerScreen(
            icon = Icons.Filled.LocationOn,
            title = translatedStringResource(R.string.capture_location_permission_title),
            body = translatedStringResource(R.string.capture_location_permission_body),
            onContinue = { permissionStep = CapturePermissionStep.LOCATION_REQUEST },
        )
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(title = translatedStringResource(R.string.capture_title), onBackClick = onDismiss)
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
                    label = translatedStringResource(R.string.capture_analyzing_photo_label),
                    detail = translatedStringResource(R.string.capture_analyzing_photo_detail),
                )
                CaptureStage.PREPARING_FORM -> AnalyzingStep(
                    label = translatedStringResource(R.string.capture_preparing_form_label),
                    detail = translatedStringResource(R.string.capture_preparing_form_detail),
                )
                CaptureStage.FORM, CaptureStage.DUPLICATE_CHECK -> FormStep(
                    uiState = uiState,
                    viewModel = viewModel,
                    viaShake = viaShake,
                    onAddPhotoClick = ::requestFormPhotoCapture,
                )
                CaptureStage.SUBMITTING -> AnalyzingStep(
                    label = translatedStringResource(R.string.capture_submitting_label),
                    detail = translatedStringResource(R.string.capture_submitting_detail),
                )
                CaptureStage.SUBMITTED -> Unit
            }

            if (uiState.stage == CaptureStage.DUPLICATE_CHECK && uiState.duplicateCandidate != null) {
                val duplicate = uiState.duplicateCandidate
                AlertDialog(
                    onDismissRequest = viewModel::dismissDuplicateDialog,
                    title = { Text(translatedStringResource(R.string.capture_duplicate_dialog_title)) },
                    text = { Text(translatedStringResource(R.string.capture_duplicate_dialog_message, duplicate?.category?.name.orEmpty())) },
                    confirmButton = {
                        TextButton(onClick = viewModel::submitAnyway) { Text(translatedStringResource(R.string.capture_duplicate_submit_anyway)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            duplicate?.id?.let(onViewExistingReport)
                            viewModel.dismissDuplicateDialog()
                        }) { Text(translatedStringResource(R.string.capture_duplicate_view_existing)) }
                    },
                )
            }
        }
    }
}

// Once a shake-triggered photo lands (stage advances off IDLE), asks the OS's own lock screen
// (KeyguardManager) to run if the device is currently locked - the ONLY thing this fires for is
// getting past the point where the camera intent has already returned, since that camera intent
// itself is allowed to run over the lock screen (see MainActivity's own doc comment) but
// everything after it - AI classification, editing the report, and especially submit() - should
// not be reachable without the user's real PIN/pattern/biometric. requestDismissKeyguard hands
// off to the exact same system unlock UI a normal screen-on would show; this never builds its own
// lock prompt. A no-op if the device is already unlocked (isKeyguardLocked() is false) or on API
// levels below 26 where requestDismissKeyguard doesn't exist (device just stays as it was, no
// crash - shake-to-report while locked is a nice-to-have here, not a hard requirement below O).
@Composable
private fun RequireDeviceUnlockOncePhotoCaptured(stage: CaptureStage) {
    val context = LocalContext.current
    var hasRequestedUnlock by remember { mutableStateOf(false) }
    LaunchedEffect(stage) {
        if (stage == CaptureStage.IDLE || hasRequestedUnlock) return@LaunchedEffect
        hasRequestedUnlock = true
        // FEATURE (explicit user request, 2026-11-18): shake-triggered capture bypasses the
        // app's own PIN lock screen while the camera is opening (see MainActivity's
        // isShakeCaptureInProgress/needsAppLockUnlock) so the shake isn't silently swallowed
        // behind it - this is the matching "ask for it afterward" half: the instant a photo/video
        // is actually captured (same trigger as the system-keyguard dismiss request right below),
        // the bypass ends, so AppLockScreen's overlay reappears in time to gate everything after
        // this point (AI classification, editing, submit) exactly as it always has for every
        // other entry into Capture.
        com.king.mob_app_assignment.util.AppLockSessionState.markShakeCaptureFinished()
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            ?: return@LaunchedEffect
        if (keyguardManager.isKeyguardLocked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(activity, null)
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
            text = translatedStringResource(R.string.capture_chooser_no_media_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = translatedStringResource(R.string.capture_chooser_no_media_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryActionButton(
                text = translatedStringResource(R.string.capture_take_photo),
                onClick = onTakePhoto,
                icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            )
            IconButton(
                onClick = onChooseFromGallery,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = translatedStringResource(R.string.capture_choose_from_gallery),
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
                Text(text = translatedStringResource(R.string.capture_record_video), modifier = Modifier.padding(start = 8.dp))
            }
            IconButton(
                onClick = onChooseVideoFromGallery,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = translatedStringResource(R.string.capture_choose_video_from_gallery),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // AI classification only ever runs on photos - video reports skip it entirely (see
        // CaptureViewModel.submit()) so the note sets expectations up front rather than the user
        // discovering the category field is suddenly required only once they reach the form.
        Text(
            text = translatedStringResource(R.string.capture_video_ai_note),
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
            text = translatedStringResource(R.string.capture_review_video_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = translatedStringResource(R.string.capture_review_media_message),
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
                Text(translatedStringResource(R.string.capture_retake), style = MaterialTheme.typography.labelLarge)
            }
            PrimaryActionButton(
                text = translatedStringResource(R.string.capture_confirm),
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
                contentDescription = translatedStringResource(R.string.capture_photo_content_description),
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
                text = translatedStringResource(R.string.capture_annotation_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                enabled = strokes.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = translatedStringResource(R.string.capture_undo_stroke))
            }
            IconButton(
                onClick = { strokes.clear() },
                enabled = strokes.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Clear, contentDescription = translatedStringResource(R.string.capture_clear_annotation))
            }
        }

        Text(
            text = translatedStringResource(R.string.capture_review_photo_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = translatedStringResource(R.string.capture_review_media_message),
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
                Text(translatedStringResource(R.string.capture_retake), style = MaterialTheme.typography.labelLarge)
            }
            PrimaryActionButton(
                text = translatedStringResource(R.string.capture_confirm),
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
                label = translatedStringResource(R.string.capture_metadata_location_label),
                value = address.ifBlank { translatedStringResource(R.string.capture_metadata_location_unavailable) } + "  ($latitude, $longitude)",
            )
            MetadataRow(
                icon = Icons.Filled.Explore,
                label = translatedStringResource(R.string.capture_metadata_heading_label),
                value = "${headingDegrees.toInt()}° $headingCardinal",
            )
            MetadataRow(
                icon = Icons.Filled.Schedule,
                label = translatedStringResource(R.string.capture_metadata_captured_label),
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        text = translatedStringResource(R.string.capture_category_title),
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
                text = translatedStringResource(R.string.capture_category_ai_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { isExpanded = true }) {
                Text(translatedStringResource(R.string.capture_choose_manually))
            }
        }
    } else {
        Text(
            text = if (isRequired) {
                translatedStringResource(R.string.capture_category_video_required_hint)
            } else {
                translatedStringResource(R.string.capture_category_ai_recheck_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // BUG FIX (found via manual testing, 2026-11-17): a LazyRow here clipped the last
        // category chip (WATER_LEAK) at the screen edge with no scroll-affordance (no fade, no
        // arrow) to hint more categories existed off-screen - a user had no visual reason to
        // suspect there was anything to swipe to, so that category was effectively undiscoverable
        // without stumbling on it by accident. FlowRow wraps every chip onto as many lines as
        // needed instead, so the full category set is always visible with no scrolling required.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportCategory.selectableEntries.forEach { category ->
                CategoryChip(
                    // BUG FIX (found via manual testing, 2026-11-20): this used the raw enum
                    // name (e.g. "WATER_LEAK") instead of the same displayLabel() every other
                    // category chip in the app uses (e.g. Admin's report filters show "Water
                    // leak") - inconsistent, shouty, and exposes an internal identifier to users.
                    label = category.displayLabel(),
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormStep(
    uiState: CaptureUiState,
    viewModel: CaptureViewModel,
    viaShake: Boolean,
    onAddPhotoClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val videoUri = uiState.videoUri
    val isVideo = videoUri != null
    var showFullMedia by remember { mutableStateOf(false) }

    // Scrollable (2026-09-11, user-reported "I took a picture, the Submit button isn't there" on
    // a real device) - this used to be a plain, non-scrolling Column on the theory that shrinking
    // the always-visible photo preview down to CaptureThumbnail (see below) would make the whole
    // form fit on one screen without needing to scroll. That held on the devices/emulator this was
    // tested on, but is not actually guaranteed: a smaller physical screen, larger system font
    // scale, or a taller on-screen nav bar can all push the real content height (thumbnail +
    // category chips + note field + metadata card + anonymous toggle + Submit button) past the
    // available height, and a plain non-scrolling Column simply clips whatever doesn't fit off the
    // bottom of the screen rather than showing it - which silently ate the Submit button entirely,
    // with no error or visual sign anything was missing. verticalScroll is a no-op on a screen
    // where the content already fits (nothing to scroll, nothing changes visually), so this can't
    // regress the "fits on one screen" case this was originally built for - it only helps the case
    // where it doesn't.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp),
    ) {
        // Shake-to-report already has exactly one photo/video by the time it reaches FORM (see
        // CaptureViewModel.onConfirmCapture) and keeps the original single-thumbnail display
        // unchanged. The form-first flow (Report Issue button, etc) instead shows a full add/
        // remove photo grid, since it can start with zero photos and end with several.
        if (viaShake || isVideo) {
            CaptureThumbnail(
                photoUri = uiState.photoUri,
                videoUri = videoUri,
                onClick = { showFullMedia = true },
            )
        } else {
            PhotoGrid(
                photoUris = listOfNotNull(uiState.photoUri) + uiState.additionalPhotoUris,
                onAddClick = onAddPhotoClick,
                onRemoveAt = viewModel::onFormPhotoRemoved,
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
                label = { Text(translatedStringResource(R.string.capture_describe_issue_label)) },
                placeholder = { Text(translatedStringResource(R.string.capture_describe_issue_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                supportingText = { Text(translatedStringResource(R.string.capture_char_count, uiState.customLabel.length, 80)) },
            )
        }

        OutlinedTextField(
            value = uiState.note,
            onValueChange = viewModel::onNoteChange,
            label = { Text(translatedStringResource(R.string.capture_note_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = MaterialTheme.shapes.small,
            supportingText = { Text(translatedStringResource(R.string.capture_char_count, uiState.note.length, 500)) },
        )

        Text(
            text = translatedStringResource(R.string.capture_report_details_title),
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
                        Text(text = translatedStringResource(R.string.capture_anonymous_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = translatedStringResource(R.string.capture_anonymous_description),
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
            text = translatedStringResource(R.string.capture_submit),
            onClick = viewModel::submit,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showFullMedia) {
        FullMediaDialog(
            photoUri = uiState.photoUri,
            videoUri = videoUri,
            onDismiss = { showFullMedia = false },
        )
    }
}

/**
 * Small tappable preview of the captured photo/video on the report form - replaces what used to
 * be a full-width 3:4 image/video permanently occupying a large share of the screen, which was
 * the main reason the form needed to scroll. Tapping it opens [FullMediaDialog] for anyone who
 * actually wants to inspect the capture closely before submitting.
 */
@Composable
private fun CaptureThumbnail(photoUri: Uri?, videoUri: Uri?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        ) {
            if (videoUri != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = photoUri,
                    contentDescription = translatedStringResource(R.string.capture_photo_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = if (videoUri != null) {
                    translatedStringResource(R.string.capture_video_captured_label)
                } else {
                    translatedStringResource(R.string.capture_photo_captured_label)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(translatedStringResource(R.string.capture_tap_to_view_full_size))
            }
        }
    }
}

/**
 * "Add Photos / Videos" grid for the form-first capture flow - a row of square thumbnails (each
 * with a small remove badge) followed by a dashed "Add more" tile, matching the reference
 * mockup's layout. Unlike [CaptureThumbnail] (shake-to-report's single always-present preview),
 * this can render with zero photos - the grid then shows just the "Add more" tile on its own.
 */
@Composable
private fun PhotoGrid(
    photoUris: List<Uri>,
    onAddClick: () -> Unit,
    onRemoveAt: (Int) -> Unit,
) {
    Column {
        Text(
            text = translatedStringResource(R.string.capture_add_photos_title),
            style = MaterialTheme.typography.titleSmall,
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(photoUris.size) { index ->
                PhotoGridThumbnail(
                    uri = photoUris[index],
                    onRemoveClick = { onRemoveAt(index) },
                )
            }
            item {
                AddPhotoTile(onClick = onAddClick)
            }
        }
    }
}

@Composable
private fun PhotoGridThumbnail(uri: Uri, onRemoveClick: () -> Unit) {
    Box(modifier = Modifier.size(80.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = translatedStringResource(R.string.capture_photo_content_description),
            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(color = Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = translatedStringResource(R.string.capture_remove_photo),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = translatedStringResource(R.string.capture_add_more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Full-screen on-demand view of the capture, opened from [CaptureThumbnail]. */
@Composable
private fun FullMediaDialog(photoUri: Uri?, videoUri: Uri?, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (videoUri != null) {
                VideoPlayer(uri = videoUri, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = photoUri,
                    contentDescription = translatedStringResource(R.string.capture_photo_content_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Clear, contentDescription = translatedStringResource(R.string.capture_close_action), tint = Color.White)
            }
        }
    }
}
