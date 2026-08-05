package com.king.mob_app_assignment.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.king.mob_app_assignment.domain.model.ReportCategory
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
        val categoryNames = ReportCategory.selectableEntries.joinToString(", ") { it.name }

        val requestJson = JSONObject().apply {
            put("model", VisionAiConfig.MODEL_ID)
            put("temperature", 0.0)
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
                                        put(
                                            "text",
                                            "You are classifying a photo of a civic infrastructure issue. " +
                                                "Reply with exactly one word from this list, nothing else: " +
                                                "$categoryNames. If the issue is clearly something else " +
                                                "specific (e.g. a fallen tree, a burst pipe, a downed power " +
                                                "line) that isn't covered by that list, reply instead with " +
                                                "CUSTOM: followed by a short 2-5 word description, e.g. " +
                                                "\"CUSTOM: Fallen tree blocking road\". Only use OTHER if you " +
                                                "genuinely cannot tell what the issue is.",
                                        )
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

    private fun parseCategory(responseBody: String): CategorySuggestion? {
        val rawContent = JSONObject(responseBody)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()

        if (rawContent.isBlank()) return null

        val customMatch = Regex("CUSTOM:\\s*(.+)", RegexOption.IGNORE_CASE).find(rawContent)
        if (customMatch != null) {
            val label = customMatch.groupValues[1].trim().take(80)
            if (label.isNotBlank()) {
                return CategorySuggestion(ReportCategory.CUSTOM, confidence = 0.75f, customLabel = label)
            }
        }

        val upperContent = rawContent.uppercase()
        val matched = ReportCategory.selectableEntries.firstOrNull { upperContent.contains(it.name) }
            ?: return null
        return CategorySuggestion(matched, confidence = 0.85f)
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
