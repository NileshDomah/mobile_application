package com.king.mob_app_assignment.ai

import android.net.Uri
import com.king.mob_app_assignment.sensor.CategorySuggestion
import com.king.mob_app_assignment.sensor.MlKitCategoryClassifier
import javax.inject.Inject
import javax.inject.Singleton

// Tries the (optional, LAN-only) vision LLM server first for a smarter suggestion;
// silently falls back to the on-device ML Kit classifier if the server is unreachable
// (different Wi-Fi, dev PC off, timeout) so the app always works standalone. See
// PLACEHOLDERS_TODO.csv for the plan to swap VisionAiConfig to a real hosted API key.
@Singleton
class CategoryClassifier @Inject constructor(
    private val visionClassifierService: VisionClassifierService,
    private val mlKitCategoryClassifier: MlKitCategoryClassifier,
) {
    suspend fun classify(imageUri: Uri): CategorySuggestion {
        val fromVisionModel = runCatching { visionClassifierService.classify(imageUri) }.getOrNull()
        return fromVisionModel ?: mlKitCategoryClassifier.classify(imageUri)
    }
}
