package com.king.mob_app_assignment.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ChatRole { SYSTEM, USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val content: String)

/**
 * Text-only chat completion against the same hosted OpenRouter endpoint already used for photo
 * classification (VisionClassifierService, see VisionAiConfig) - reuses its exact OkHttp setup
 * and choices[0].message.content response-parsing pattern, just without the image_url content
 * part.
 */
@Singleton
class ChatMessageService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(VisionAiConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(VisionAiConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(VisionAiConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * [messages] should include the system prompt first, then the full conversation history in
     * order, ending with the latest user turn. Returns null on any failure (unreachable server,
     * non-2xx, unparseable response) - callers show a fallback "assistant unavailable" message
     * rather than surfacing a raw error, same pattern as CategoryClassifier's fallback chain.
     */
    suspend fun sendMessage(messages: List<ChatMessage>): String? = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put("model", VisionAiConfig.MODEL_ID)
            put("temperature", 0.3)
            put("max_tokens", 400)
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject().apply {
                                put("role", message.role.name.lowercase())
                                put("content", message.content)
                            },
                        )
                    }
                },
            )
        }

        val request = Request.Builder()
            .url("${VisionAiConfig.BASE_URL}/v1/chat/completions")
            .apply {
                VisionAiConfig.API_KEY?.let { key -> addHeader("Authorization", "Bearer $key") }
                // OpenRouter-recommended (not strictly required) attribution headers - see
                // VisionClassifierService's identical addition for why.
                addHeader("HTTP-Referer", "https://github.com/king/mob-app-assignment")
                addHeader("X-Title", "Shake to Report")
            }
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseReply(body)
            }
        }.getOrNull()
    }

    private fun parseReply(responseBody: String): String? {
        val content = JSONObject(responseBody)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
        return content.ifBlank { null }
    }
}
