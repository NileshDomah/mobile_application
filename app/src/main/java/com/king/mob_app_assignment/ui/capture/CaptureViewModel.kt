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
import com.king.mob_app_assignment.ai.IncidentClusteringWorker
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
    // Distinct from ANALYZING - that stage's copy ("Analyzing photo...") is specifically about
    // the shake flow's post-photo-capture wait. startFormFirstFlow() reused ANALYZING for its own
    // brief GPS/heading fetch before any photo exists yet, which showed the exact same "Analyzing
    // photo" text on a screen where the user hadn't taken a photo at all - a real, reported bug.
    // This stage exists so the form-first flow can show its own, correctly-worded loading state.
    PREPARING_FORM,
    FORM,
    DUPLICATE_CHECK,
    SUBMITTING,
    SUBMITTED,
}

private const val DUPLICATE_CHECK_RADIUS_METERS = 100.0
private const val DUPLICATE_CHECK_WINDOW_MINUTES = 10L

data class CaptureUiState(
    val stage: CaptureStage = CaptureStage.IDLE,
    // photoUri/photoFile is always the FIRST photo added (the "primary" one) - the one shown in
    // the shake-to-report PREVIEW/annotator flow, the one EXIF-stripped, classified by AI, and
    // written to Report.localPhotoPath for back-compat with every existing report document (which
    // predates multi-photo support and only ever had one photo field). additionalPhotoFiles holds
    // every photo after the first, added via the form-first flow's "Add more" tile - shake-to-
    // report never populates this list, since that flow captures exactly one photo and jumps
    // straight to PREVIEW/confirm, bypassing the form-first "Add Photos" grid entirely.
    val photoUri: Uri? = null,
    val photoFile: File? = null,
    val additionalPhotoUris: List<Uri> = emptyList(),
    val additionalPhotoFiles: List<File> = emptyList(),
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

    // Entry point for the form-first flow (tapping "Report Issue", as opposed to shake-to-report
    // which calls onPhotoCaptured directly and goes through the PREVIEW/ANALYZING pipeline
    // instead) - lands straight on an empty FORM with no photo yet. Location/heading are captured
    // immediately rather than waiting for a first photo, since the form needs them to render its
    // metadata card regardless of whether/when a photo gets added.
    fun startFormFirstFlow() {
        _uiState.update { it.copy(stage = CaptureStage.PREPARING_FORM) }
        viewModelScope.launch {
            val captureTimestamp = System.currentTimeMillis()
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

    // Appends a photo captured/picked from the form-first flow's "Add Photos" tile. The first
    // photo added becomes the primary photoUri/photoFile (see CaptureUiState's doc comment);
    // every one after that goes into additionalPhotoFiles. EXIF-stripped immediately on capture
    // (unlike the shake flow, which strips in onConfirmCapture) since there is no separate
    // confirm step in the form-first flow for each individual photo added this way.
    fun onFormPhotoAdded(uri: Uri, file: File) {
        ExifStripper.stripSensitiveTags(file)
        _uiState.update { current ->
            if (current.photoFile == null) {
                current.copy(photoUri = uri, photoFile = file)
            } else {
                current.copy(
                    additionalPhotoUris = current.additionalPhotoUris + uri,
                    additionalPhotoFiles = current.additionalPhotoFiles + file,
                )
            }
        }
    }

    // Removes a photo from the form-first flow's grid by its position among ALL photos (primary
    // first, then additional ones in order) - lets the grid render one flat list of thumbnails
    // without the caller needing to know about the primary/additional split internally. Removing
    // index 0 promotes the next photo (if any) to primary.
    fun onFormPhotoRemoved(index: Int) {
        _uiState.update { current ->
            val allUris = listOfNotNull(current.photoUri) + current.additionalPhotoUris
            val allFiles = listOfNotNull(current.photoFile) + current.additionalPhotoFiles
            if (index !in allFiles.indices) return@update current
            val newUris = allUris.toMutableList().apply { removeAt(index) }
            val newFiles = allFiles.toMutableList().apply { removeAt(index) }
            current.copy(
                photoUri = newUris.firstOrNull(),
                photoFile = newFiles.firstOrNull(),
                additionalPhotoUris = newUris.drop(1),
                additionalPhotoFiles = newFiles.drop(1),
            )
        }
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
            // BUG FIX (found via manual testing, 2026-11-17): this fired on the ordinary
            // "tapped Submit without ever adding a photo" case too, not just a genuine capture
            // failure - "please retake it" is wrong and confusing when nothing was ever taken.
            _uiState.update { it.copy(errorMessage = "Add a photo before submitting your report") }
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
        // Denormalized onto the report so admin-facing screens can show who filed it without a
        // per-report /users/{userId} lookup - see Report.reporterName's own doc comment. Read
        // straight off FirebaseAuth's own cached profile (set once at registration via
        // updateProfile(), see AuthRepositoryImpl.register()) rather than a fresh Firestore read,
        // since it's already in memory and this is just a point-in-time snapshot anyway. Blank
        // for an anonymous report, matching userId above.
        val reporterName = if (state.isAnonymous) "" else firebaseAuth.currentUser?.displayName.orEmpty()
        // The real trim-both-ends pass happens here, once, at actual submit time - not on every
        // keystroke (see NoteSanitizer.sanitizeNote's own doc comment for why trimming live used
        // to make the space bar look broken).
        val submittedNote = NoteSanitizer.sanitizeNoteForSubmit(state.note)
        val report = Report(
            userId = userId,
            reporterName = reporterName,
            isAnonymous = state.isAnonymous,
            category = category,
            customLabel = if (category == ReportCategory.CUSTOM) state.customLabel.trim() else "",
            mediaType = state.mediaType,
            localPhotoPath = photoFile?.absolutePath,
            additionalLocalPhotoPaths = state.additionalPhotoFiles.map { it.absolutePath },
            localVideoPath = videoFile?.absolutePath,
            latitude = state.latitude,
            longitude = state.longitude,
            address = state.address,
            headingDegrees = state.headingDegrees,
            headingCardinal = state.headingCardinal,
            timestamp = state.timestamp,
            note = submittedNote,
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
            // Incident clustering runs for every report regardless of media type - unlike AI
            // classification, it only needs category/location/timestamp (all present for video
            // reports too), not the photo/video content itself.
            if (!submittedId.isNullOrBlank()) {
                enqueueIncidentClusteringWork(submittedId)
            }
        } else {
            result.exceptionOrNull()?.let {
                CrashReporter.log("CaptureViewModel: report submission failed, queuing locally")
                CrashReporter.recordException(it)
            }
            val entity = PendingReportEntity(
                userId = userId,
                reporterName = reporterName,
                category = category.name,
                customLabel = report.customLabel,
                localPhotoPath = photoFile?.absolutePath.orEmpty(),
                additionalLocalPhotoPaths = state.additionalPhotoFiles.joinToString(
                    com.king.mob_app_assignment.data.local.PendingReportEntity.ADDITIONAL_PHOTO_PATHS_DELIMITER,
                ) { it.absolutePath },
                mediaType = state.mediaType.name,
                localVideoPath = videoFile?.absolutePath.orEmpty(),
                isAnonymous = state.isAnonymous,
                latitude = state.latitude,
                longitude = state.longitude,
                address = state.address,
                headingDegrees = state.headingDegrees,
                headingCardinal = state.headingCardinal,
                timestamp = state.timestamp,
                note = submittedNote,
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

    // Same durability rationale as enqueueClassificationWork - a plain coroutine would die if
    // the process is killed right after submit. No network constraint here (unlike
    // classification): clustering only reads/writes Firestore directly, and Firestore's own
    // offline persistence/write queue already handles connectivity gaps without WorkManager
    // needing to gate on NetworkType.CONNECTED itself.
    private fun enqueueIncidentClusteringWork(reportId: String) {
        val request = OneTimeWorkRequestBuilder<IncidentClusteringWorker>()
            .setInputData(Data.Builder().putString(IncidentClusteringWorker.KEY_REPORT_ID, reportId).build())
            .build()
        workManager.enqueue(request)
    }

    fun reset() {
        _uiState.update { CaptureUiState() }
    }
}
