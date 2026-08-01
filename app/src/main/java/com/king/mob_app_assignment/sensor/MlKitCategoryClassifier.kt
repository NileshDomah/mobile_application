package com.king.mob_app_assignment.sensor

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.king.mob_app_assignment.domain.model.ReportCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CategorySuggestion(
    val category: ReportCategory,
    val confidence: Float,
)

@Singleton
class MlKitCategoryClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    private val keywordMap = listOf(
        ReportCategory.POTHOLE to listOf("road", "asphalt", "pothole", "street"),
        ReportCategory.STREETLIGHT to listOf("light", "lamp", "pole", "streetlight"),
        ReportCategory.GARBAGE to listOf("trash", "waste", "garbage", "litter"),
        ReportCategory.WATER_LEAK to listOf("water", "puddle", "flood", "leak"),
    )

    suspend fun classify(imageUri: Uri): CategorySuggestion {
        val image = InputImage.fromFilePath(context, imageUri)
        val labels = labeler.process(image).await()

        for ((category, keywords) in keywordMap) {
            val match = labels.firstOrNull { label ->
                keywords.any { keyword -> label.text.lowercase().contains(keyword) }
            }
            if (match != null) {
                return CategorySuggestion(category, match.confidence)
            }
        }

        val topConfidence = labels.maxOfOrNull { it.confidence } ?: 0f
        return CategorySuggestion(ReportCategory.OTHER, topConfidence.coerceAtMost(0.49f))
    }
}
