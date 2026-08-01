package com.king.mob_app_assignment.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.king.mob_app_assignment.data.local.PendingReportDao
import com.king.mob_app_assignment.data.local.PendingReportEntity
import com.king.mob_app_assignment.data.location.LocationProvider
import com.king.mob_app_assignment.data.repository.ReportRepository
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.sensor.CompassProvider
import com.king.mob_app_assignment.sensor.MlKitCategoryClassifier
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
    val suggestedCategory: ReportCategory? = null,
    val suggestionConfidence: Float = 0f,
    val selectedCategory: ReportCategory? = null,
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "",
    val timestamp: Long = 0L,
    val errorMessage: String? = null,
    val duplicateCandidate: Report? = null,
)

private const val CONFIDENCE_AUTO_SELECT_THRESHOLD = 0.5f

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val compassProvider: CompassProvider,
    private val mlKitCategoryClassifier: MlKitCategoryClassifier,
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
        val photoUri = current.photoUri ?: return
        val photoFile = current.photoFile

        _uiState.update { it.copy(stage = CaptureStage.ANALYZING, errorMessage = null) }

        viewModelScope.launch {
            photoFile?.let { ExifStripper.stripSensitiveTags(it) }

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
            val classificationDeferred = async {
                runCatching { mlKitCategoryClassifier.classify(photoUri) }.getOrNull()
            }

            val (lat, lng, address) = locationDeferred.await()
            val heading = headingDeferred.await()
            val suggestion = classificationDeferred.await()

            val autoSelected = suggestion?.takeIf {
                it.confidence >= CONFIDENCE_AUTO_SELECT_THRESHOLD
            }?.category

            _uiState.update {
                it.copy(
                    stage = CaptureStage.FORM,
                    latitude = lat,
                    longitude = lng,
                    address = address,
                    headingDegrees = heading,
                    headingCardinal = CompassProvider.degreesToCardinal(heading),
                    timestamp = captureTimestamp,
                    suggestedCategory = suggestion?.category,
                    suggestionConfidence = suggestion?.confidence ?: 0f,
                    selectedCategory = autoSelected,
                )
            }
        }
    }

    fun onCategorySelected(category: ReportCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = NoteSanitizer.sanitizeNote(value)) }
    }

    fun submit() {
        val state = _uiState.value
        val category = state.selectedCategory
        val photoFile = state.photoFile
        if (category == null || photoFile == null) {
            _uiState.update { it.copy(errorMessage = "Choose a category before submitting") }
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
        val category = state.selectedCategory ?: return
        val photoFile = state.photoFile ?: return

        _uiState.update { it.copy(stage = CaptureStage.SUBMITTING, errorMessage = null) }

        val userId = firebaseAuth.currentUser?.uid.orEmpty()
        val report = Report(
            userId = userId,
            category = category,
            localPhotoPath = photoFile.absolutePath,
            latitude = state.latitude,
            longitude = state.longitude,
            address = state.address,
            headingDegrees = state.headingDegrees,
            headingCardinal = state.headingCardinal,
            timestamp = state.timestamp,
            note = state.note,
        )

        val result = reportRepository.submitReport(report, photoFile)
        if (result.isSuccess) {
            _uiState.update { it.copy(stage = CaptureStage.SUBMITTED) }
        } else {
            val entity = PendingReportEntity(
                userId = userId,
                category = category.name,
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
            _uiState.update { it.copy(stage = CaptureStage.SUBMITTED) }
        }
    }

    fun reset() {
        _uiState.update { CaptureUiState() }
    }
}
