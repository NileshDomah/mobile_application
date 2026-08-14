package com.king.mob_app_assignment.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.ai.ClassificationWorker
import com.king.mob_app_assignment.data.local.PendingReportDao
import com.king.mob_app_assignment.data.local.PendingReportEntity
import com.king.mob_app_assignment.data.location.LocationProvider
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportMediaType
import com.king.mob_app_assignment.sensor.CompassProvider
import com.king.mob_app_assignment.util.CrashReporter
import com.king.mob_app_assignment.util.ExifStripper
import com.king.mob_app_assignment.util.NoteSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class CaptureStage {
    IDLE,
    PREVIEW,
    ANALYZING,
    FORM,
    DUPLICATE_CHECK,
    SUBMITTING,
    SUBMITTED,
}

private const val DUPLICATE_CHECK_RADIUS_METERS = 100.0
private const val DUPLICATE_CHECK_WINDOW_MINUTES = 10L

data class CaptureUiState(
    val stage: CaptureStage = CaptureStage.IDLE,
    val photoUri: Uri? = null,
    val photoFile: File? = null,
    // Set instead of photoUri/photoFile when the user recorded/picked a video. Mutually
    // exclusive with photoUri/photoFile by construction - see onVideoCaptured vs onPhotoCaptured.
    val videoUri: Uri? = null,
    val videoFile: File? = null,
    val mediaType: ReportMediaType = ReportMediaType.PHOTO,
    val selectedCategory: ReportCategory? = null,
    val customLabel: String = "",
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "",
    val timestamp: Long = 0L,
    val errorMessage: String? = null,
    val duplicateCandidate: Report? = null,
    val wasQueuedOffline: Boolean = false,
    val isAnonymous: Boolean = false,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val compassProvider: CompassProvider,
    private val pendingReportDao: PendingReportDao,
    private val reportRepository: ReportRepository,
    private val firebaseAuth: FirebaseAuth,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onPhotoCaptured(uri: Uri, file: File) {
        _uiState.update {
            CaptureUiState(stage = CaptureStage.PREVIEW, photoUri = uri, photoFile = file, mediaType = ReportMediaType.PHOTO)
        }
    }

    // Video reports skip AI classification entirely (see submit()'s manual-category requirement
    // below) - the user's explicit choice was to avoid AI video analysis and just require manual
    // category input for video reports instead.
    fun onVideoCaptured(uri: Uri, file: File) {
        _uiState.update {
            CaptureUiState(stage = CaptureStage.PREVIEW, videoUri = uri, videoFile = file, mediaType = ReportMediaType.VIDEO)
        }
    }

    fun onAnonymousToggle(enabled: Boolean) {
        _uiState.update { it.copy(isAnonymous = enabled) }
    }

    fun onRetake() {
        _uiState.update { CaptureUiState(stage = CaptureStage.IDLE) }
    }

    fun onConfirmCapture() {
        val current = _uiState.value
        val photoFile = current.photoFile

        _uiState.update { it.copy(stage = CaptureStage.ANALYZING, errorMessage = null) }

        viewModelScope.launch {
            // EXIF stripping only applies to photos - video files don't carry the same
            // location-tagged EXIF metadata risk (video metadata isn't touched by ExifInterface).
            photoFile?.let { ExifStripper.stripSensitiveTags(it) }

            val captureTimestamp = System.currentTimeMillis()

            // AI classification deliberately does NOT run here anymore - it used to block the
            // user on this screen while Gemma/ML Kit processed the photo. It now runs in the
            // background after submit (see AiReviewCoordinator), so the user only ever waits on
            // location/heading, which are fast local device reads, not a network/model call.
            val locationDeferred = async {
                runCatching {
                    val location = locationProvider.getCurrentLocation()
                    val address = location?.let {
                        locationProvider.reverseGeocode(it.latitude, it.longitude)
                    }
                    Triple(location?.latitude ?: 0.0, location?.longitude ?: 0.0, address.orEmpty())
                }.getOrElse { Triple(0.0, 0.0, "") }
            }
            val headingDeferred = async {
                runCatching { compassProvider.headingDegreesFlow().first() }.getOrElse { 0f }
            }

            val (lat, lng, address) = locationDeferred.await()
            val heading = headingDeferred.await()

            _uiState.update {
                it.copy(
                    stage = CaptureStage.FORM,
                    latitude = lat,
                    longitude = lng,
                    address = address,
                    headingDegrees = heading,
                    headingCardinal = CompassProvider.degreesToCardinal(heading),
                    timestamp = captureTimestamp,
                )
            }
        }
    }

    fun onCategorySelected(category: ReportCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onCustomLabelChange(value: String) {
        _uiState.update { it.copy(customLabel = value.take(80)) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = NoteSanitizer.sanitizeNote(value)) }
    }

    fun submit() {
        val state = _uiState.value
        val isVideo = state.mediaType == ReportMediaType.VIDEO

        if (isVideo) {
            if (state.videoFile == null) {
                _uiState.update { it.copy(errorMessage = "Something went wrong with the video - please retake it") }
                return
            }
            // Category selection is REQUIRED for video reports - the user's explicit choice was
            // to avoid AI video analysis entirely, so there's no background classifier that can
            // fill this in later the way AiReviewCoordinator does for photos.
            if (state.selectedCategory == null) {
                _uiState.update { it.copy(errorMessage = "Please choose a category for this video report") }
                return
            }
        } else if (state.photoFile == null) {
            _uiState.update { it.copy(errorMessage = "Something went wrong with the photo - please retake it") }
            return
        }

        // For photos, category selection stays optional: if skipped, the report submits as
        // UNCATEGORIZED and AiReviewCoordinator fills in the real category in the background
        // (see doSubmit()/enqueueClassificationWork). Video reports can't reach this fallback -
        // selectedCategory is guaranteed non-null for video by the check above.
        val category = state.selectedCategory ?: ReportCategory.UNCATEGORIZED
        if (category == ReportCategory.CUSTOM && state.customLabel.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Describe the issue before submitting") }
            return
        }

        _uiState.update { it.copy(errorMessage = null) }

        viewModelScope.launch {
            val duplicate = runCatching {
                reportRepository.findNearbyRecentReports(
                    category = category,
                    lat = state.latitude,
                    lng = state.longitude,
                    radiusMeters = DUPLICATE_CHECK_RADIUS_METERS,
                    withinMinutes = DUPLICATE_CHECK_WINDOW_MINUTES,
                )
            }.getOrNull()?.firstOrNull()

            if (duplicate != null) {
                _uiState.update { it.copy(stage = CaptureStage.DUPLICATE_CHECK, duplicateCandidate = duplicate) }
            } else {
                doSubmit()
            }
        }
    }

    fun dismissDuplicateDialog() {
        _uiState.update { it.copy(stage = CaptureStage.FORM, duplicateCandidate = null) }
    }

    fun submitAnyway() {
        _uiState.update { it.copy(duplicateCandidate = null) }
        viewModelScope.launch { doSubmit() }
    }

    private suspend fun doSubmit() {
        val state = _uiState.value
        val isVideo = state.mediaType == ReportMediaType.VIDEO
        val category = state.selectedCategory ?: ReportCategory.UNCATEGORIZED
        val photoFile = state.photoFile
        val videoFile = state.videoFile
        if (isVideo && videoFile == null) return
        if (!isVideo && photoFile == null) return

        _uiState.update { it.copy(stage = CaptureStage.SUBMITTING, errorMessage = null) }

        // Anonymous reports store no owner at all (empty userId, isAnonymous = true) - see
        // Report.isAnonymous doc. The submitter still needs to be signed in to use the app (this
        // is enforced elsewhere, not here), but that identity is deliberately never attached to
        // the report document.
        val userId = if (state.isAnonymous) "" else firebaseAuth.currentUser?.uid.orEmpty()
        val report = Report(
            userId = userId,
            isAnonymous = state.isAnonymous,
            category = category,
            customLabel = if (category == ReportCategory.CUSTOM) state.customLabel.trim() else "",
            mediaType = state.mediaType,
            localPhotoPath = photoFile?.absolutePath,
            localVideoPath = videoFile?.absolutePath,
            latitude = state.latitude,
            longitude = state.longitude,
            address = state.address,
            headingDegrees = state.headingDegrees,
            headingCardinal = state.headingCardinal,
            timestamp = state.timestamp,
            note = state.note,
        )

        CrashReporter.log("CaptureViewModel: submitting report category=${category.name} mediaType=${state.mediaType.name}")
        val result = reportRepository.submitReport(report, photoFile)
        if (result.isSuccess) {
            CrashReporter.log("CaptureViewModel: report submitted successfully")
            _uiState.update { it.copy(stage = CaptureStage.SUBMITTED) }
            val submittedId = result.getOrNull()?.id
            // Video reports never get AI classification (see submit()'s manual-category
            // requirement) - no point enqueuing a worker whose Uri points at a video file the
            // photo-only classifiers can't decode.
            if (!isVideo && !submittedId.isNullOrBlank() && photoFile != null) {
                enqueueClassificationWork(submittedId, photoFile, category)
            }
        } else {
            result.exceptionOrNull()?.let {
                CrashReporter.log("CaptureViewModel: report submission failed, queuing locally")
                CrashReporter.recordException(it)
            }
            val entity = PendingReportEntity(
                userId = userId,
                category = category.name,
                customLabel = report.customLabel,
                localPhotoPath = photoFile?.absolutePath.orEmpty(),
                mediaType = state.mediaType.name,
                localVideoPath = videoFile?.absolutePath.orEmpty(),
                isAnonymous = state.isAnonymous,
                latitude = state.latitude,
                longitude = state.longitude,
                address = state.address,
                headingDegrees = state.headingDegrees,
                headingCardinal = state.headingCardinal,
                timestamp = state.timestamp,
                note = state.note,
                status = "SUBMITTED",
            )
            pendingReportDao.insert(entity)
            _uiState.update { it.copy(stage = CaptureStage.SUBMITTED, wasQueuedOffline = true) }
        }
    }

    // Enqueues classification as a WorkManager job rather than calling AiReviewCoordinator
    // directly on appScope - a plain coroutine dies if the app process is killed right after
    // submit (user swipes the app away), while a WorkManager job is durable: it survives process
    // death and even a device reboot, and only runs once NetworkType.CONNECTED is satisfied
    // (classification needs the LAN AI server or, at minimum, an on-device ML Kit run that still
    // benefits from not fighting for CPU/network while the device is offline anyway).
    private fun enqueueClassificationWork(reportId: String, photoFile: File, userSelectedCategory: ReportCategory) {
        val request = OneTimeWorkRequestBuilder<ClassificationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                Data.Builder()
                    .putString(ClassificationWorker.KEY_REPORT_ID, reportId)
                    .putString(ClassificationWorker.KEY_PHOTO_URI, Uri.fromFile(photoFile).toString())
                    .putString(ClassificationWorker.KEY_USER_SELECTED_CATEGORY, userSelectedCategory.name)
                    .build(),
            )
            .build()
        workManager.enqueue(request)
    }

    fun reset() {
        _uiState.update { CaptureUiState() }
    }
}
