package com.king.mob_app_assignment.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.domain.model.ReportPriority
import com.king.mob_app_assignment.sensor.CategorySuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_IMAGE_DIMENSION_PX = 768
private const val JPEG_QUALITY = 80

@Singleton
class VisionClassifierService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(VisionAiConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(VisionAiConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(VisionAiConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun classify(imageUri: Uri): CategorySuggestion? = withContext(Dispatchers.IO) {
        val base64Image = encodeImage(imageUri) ?: return@withContext null

        val requestJson = JSONObject().apply {
            put("model", VisionAiConfig.MODEL_ID)
            put("temperature", 0.0)
            // 20 is enough for the strict two-line CATEGORY:/SEVERITY: format below (previously
            // 40, sized for the old free-text prompt) - fewer tokens the server has to generate
            // is a direct latency win on top of the accuracy gain from a stricter prompt, since
            // generation time scales with output length regardless of model size.
            put("max_tokens", 20)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", buildClassificationPrompt())
                                    },
                                )
                                .put(
                                    JSONObject().apply {
                                        put("type", "image_url")
                                        put(
                                            "image_url",
                                            JSONObject().put(
                                                "url",
                                                "data:image/jpeg;base64,$base64Image",
                                            ),
                                        )
                                    },
                                ),
                        )
                    },
                ),
            )
        }

        val request = Request.Builder()
            .url("${VisionAiConfig.BASE_URL}/v1/chat/completions")
            .apply {
                VisionAiConfig.API_KEY?.let { key -> addHeader("Authorization", "Bearer $key") }
                // OpenRouter-recommended (not strictly required) attribution headers - identifies
                // the calling app in OpenRouter's own dashboard/rankings, no effect on any other
                // backend that might sit behind VisionAiConfig.BASE_URL in the future.
                addHeader("HTTP-Referer", "https://github.com/king/mob-app-assignment")
                addHeader("X-Title", "Shake to Report")
            }
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseCategory(body)
            }
        }.getOrNull()
    }

    // Per-category visual definitions rather than bare enum names - a small local model like
    // Gemma-4-E4B does noticeably better at picking the right label when it's told WHAT to look
    // for in the photo, not just given a list of words to choose from (a bare name leaves it
    // guessing at category boundaries, e.g. whether a cracked sidewalk counts as POTHOLE). This
    // is the same kind of gain a fine-tune buys you on a small, fixed label set - concrete
    // criteria per class - without the dataset/training/quantization work fine-tuning requires.
    private fun categoryDefinitionsBlock(): String = ReportCategory.selectableEntries
        .filterNot { it == ReportCategory.CUSTOM }
        .joinToString("\n") { category ->
            val description = when (category) {
                ReportCategory.POTHOLE -> "a hole, crack, or damaged/collapsed section in a road, " +
                    "pavement, or sidewalk surface"
                ReportCategory.STREETLIGHT -> "a broken, dark, flickering, or damaged street light, " +
                    "lamp post, or traffic light"
                ReportCategory.GARBAGE -> "illegally dumped trash, an overflowing bin, or litter " +
                    "accumulated in a public space"
                ReportCategory.WATER_LEAK -> "water leaking or pooling from a pipe, hydrant, main, " +
                    "or drainage system - NOT rain puddles"
                ReportCategory.OTHER -> "any other visible public infrastructure problem not covered above"
                else -> category.name
            }
            "- ${category.name}: $description"
        }

    // Strict CATEGORY:/SEVERITY: two-line format (previously free-text with embedded keywords) -
    // easier for a small model to produce reliably and for parseCategory() to parse
    // unambiguously, both of which matter more for a 4B-parameter local model than for a larger
    // hosted one, which tends to follow loosely-specified instructions more consistently anyway.
    private fun buildClassificationPrompt(): String {
        val definitions = categoryDefinitionsBlock()
        return """
            You are classifying a photo of a civic infrastructure issue for a city report app.

            Categories:
            $definitions
            - CUSTOM: a specific issue not covered above (e.g. a fallen tree, a burst pipe, a downed power line)

            Respond with EXACTLY two lines, nothing else:
            CATEGORY: <one of POTHOLE, STREETLIGHT, GARBAGE, WATER_LEAK, OTHER, or CUSTOM: followed by a short 2-5 word description>
            SEVERITY: <one of LOW, NORMAL, HIGH, URGENT>

            Only use OTHER if you genuinely cannot tell what the issue is - prefer CUSTOM with a short description over OTHER whenever the issue is identifiable but not in the list above.
            Severity guide: a small crack or minor cosmetic issue is LOW; a typical reportable issue is NORMAL; a large pothole or major leak is HIGH; anything posing an immediate safety risk is URGENT.
        """.trimIndent()
    }

    private fun parseCategory(responseBody: String): CategorySuggestion? {
        val rawContent = JSONObject(responseBody)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()

        if (rawContent.isBlank()) return null

        val severity = Regex("SEVERITY:\\s*(\\w+)", RegexOption.IGNORE_CASE).find(rawContent)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { ReportPriority.fromFirestoreValue(it) }
            ?: ReportPriority.NORMAL

        val customMatch = Regex("CUSTOM:\\s*(.+)", RegexOption.IGNORE_CASE).find(rawContent)
        if (customMatch != null) {
            // Strip a trailing "SEVERITY: ..." line if the model put both on the CUSTOM line
            // rather than a genuinely separate line (Gemma is not perfectly consistent here).
            val label = Regex("SEVERITY", RegexOption.IGNORE_CASE)
                .split(customMatch.groupValues[1], limit = 2)
                .first()
                .trim()
                .take(80)
            if (label.isNotBlank()) {
                return CategorySuggestion(
                    ReportCategory.CUSTOM,
                    confidence = 0.75f,
                    customLabel = label,
                    priority = severity,
                )
            }
        }

        val upperContent = rawContent.uppercase()
        val matched = ReportCategory.selectableEntries.firstOrNull { upperContent.contains(it.name) }
            ?: return null
        return CategorySuggestion(matched, confidence = 0.85f, priority = severity)
    }

    private fun encodeImage(uri: Uri): String? = runCatching {
        val original = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / maxOf(original.width, original.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true,
            )
        } else {
            original
        }

        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
