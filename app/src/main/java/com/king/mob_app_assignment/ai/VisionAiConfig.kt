package com.king.mob_app_assignment.ai

// Single place to swap the vision AI backend. Currently points at a local LM Studio
// server (OpenAI-compatible /v1/chat/completions API) running Gemma 4 E4B on the
// developer's PC for testing. Switching to a real hosted API later (e.g. a paid
// Gemini/OpenAI vision endpoint) means changing only baseUrl/apiKey/modelId here -
// VisionClassifierService's request/response shape is already the standard
// OpenAI-compatible chat-completions format, which most hosted vision APIs also accept.
object VisionAiConfig {
    const val BASE_URL = "http://172.16.60.31:1234"
    const val MODEL_ID = "google/gemma-4-e4b"
    val API_KEY: String? = null
    const val REQUEST_TIMEOUT_SECONDS = 12L
}
