package com.king.mob_app_assignment.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.ai.AiReviewCoordinator
import com.king.mob_app_assignment.data.local.PendingReportDao
import com.king.mob_app_assignment.data.local.PendingReportEntity
import com.king.mob_app_assignment.data.location.LocationProvider
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.sensor.CompassProvider
import com.king.mob_app_assignment.util.CrashReporter
import com.king.mob_app_assignment.util.ExifStripper
import com.king.mob_app_assignment.util.NoteSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val compassProvider: CompassProvider,
    private val aiReviewCoordinator: AiReviewCoordinator,
    private val appScope: CoroutineScope,
    private val pendingReportDao: PendingReportDao,
    private val reportRepository: ReportRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onPhotoCaptured(uri: Uri, file: File) {
        _uiState.update {
            CaptureUiState(stage = CaptureStage.PREVIEW, photoUri = uri, photoFile = file)
        }
    }

    fun onRetake() {
        _uiState.update { CaptureUiState(stage = CaptureStage.IDLE) }
    }

    fun onConfirmCapture() {
        val current = _uiState.value
        val photoFile = current.photoFile

        _uiState.update { it.copy(stage = CaptureStage.ANALYZING, errorMessage = null) }

        viewModelScope.launch {
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
        // Category selection is optional: if the user doesn't pick one, the report submits as
        // UNCATEGORIZED and AiReviewCoordinator fills in the real category once it classifies
        // the photo in the background (see doSubmit()).
        val category = state.selectedCategory ?: ReportCategory.UNCATEGORIZED
        val photoFile = state.photoFile
        if (photoFile == null) {
            _uiState.update { it.copy(errorMessage = "Something went wrong with the photo - please retake it") }
            return
        }
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
        val category = state.selectedCategory ?: ReportCategory.UNCATEGORIZED
        val photoFile = state.photoFile ?: return

        _uiState.update { it.copy(stage = CaptureStage.SUBMITTING, errorMessage = null) }

        val userId = firebaseAuth.currentUser?.uid.orEmpty()
        val report = Report(
            userId = userId,
            category = category,
            customLabel = if (category == ReportCategory.CUSTOM) state.customLabel.trim() else "",
            localPhotoPath = photoFile.absolutePath,
            latitude = state.latitude,
            longitude = state.longitude,
            address = state.address,
            headingDegrees = state.headingDegrees,
            headingCardinal = state.headingCardinal,
            timestamp = state.timestamp,
            note = state.note,
        )

        CrashReporter.log("CaptureViewModel: submitting report category=${category.name}")
        val result = reportRepository.submitReport(report, photoFile)
        if (result.isSuccess) {
            CrashReporter.log("CaptureViewModel: report submitted successfully")
            _uiState.update { it.copy(stage = CaptureStage.SUBMITTED) }
            val submittedId = result.getOrNull()?.id
            if (!submittedId.isNullOrBlank()) {
                aiReviewCoordinator.reviewInBackground(
                    appScope = appScope,
                    reportId = submittedId,
                    photoUri = Uri.fromFile(photoFile),
                    userSelectedCategory = category,
                )
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
                localPhotoPath = photoFile.absolutePath,
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

    fun reset() {
        _uiState.update { CaptureUiState() }
    }
}
