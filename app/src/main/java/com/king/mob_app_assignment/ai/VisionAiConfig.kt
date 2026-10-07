package com.king.mob_app_assignment.ai

import com.king.mob_app_assignment.BuildConfig

// Single place to swap the vision AI backend. Both photo classification
// (VisionClassifierService) and the chatbot (ChatMessageService) share this one config, since
// both already speak the same OpenAI-compatible /v1/chat/completions request/response shape that
// OpenRouter (and most hosted vision APIs) also accept - switching backends again later means
// changing only BASE_URL/MODEL_ID/the key source here, no other code.
//
// CHANGED 2026-09-09: previously pointed at a dev-only Cloudflare tunnel in front of a local LM
// Studio server running Gemma-4-E4B (see PLACEHOLDERS_TODO.csv for that whole saga - it only ever
// worked while the developer's PC was on and cloudflared was running). Replaced with OpenRouter
// (https://openrouter.ai), a real hosted, unified API in front of many providers' models - this
// is the actual "before final submission" upgrade the old comment here was planning for.
// MODEL_ID is a vision-capable OpenRouter model slug (needed since VisionClassifierService sends
// image_url content parts, not just text) - swap it for any other OpenRouter vision model slug
// without touching VisionClassifierService/ChatMessageService at all.
//
// API_KEY is read from BuildConfig, which is generated from local.properties' OPENROUTER_API_KEY
// entry (gitignored, never committed) - see app/build.gradle.kts. Never hardcode a real key
// directly in this file; a key pasted into any chat/document/commit should be treated as
// compromised and rotated in the OpenRouter dashboard.
//
// ROOT CAUSE FOUND 2026-09-11 for user-reported "the api for ai doesn't work" - MODEL_ID was set
// to "google/gemini-2.0-flash-001", which no longer exists in OpenRouter's model catalog (their
// lineup has since moved on to the 2.5/3.x generation) - every real request returned a plain 404
// "No endpoints found for google/gemini-2.0-flash-001" from OpenRouter itself, confirmed directly
// against the API with curl using the actual configured key (which is otherwise valid and has
// working credits - this was never a key/auth problem). Swapped to google/gemini-2.5-flash, a
// current, non-preview, vision-capable model - verified with a real request before committing
// this change. If OpenRouter deprecates this one too in the future, GET
// https://openrouter.ai/api/v1/models (with this same key) lists every currently valid slug.
object VisionAiConfig {
    const val BASE_URL = "https://openrouter.ai/api"
    const val MODEL_ID = "google/gemini-2.5-flash"
    val API_KEY: String? = BuildConfig.OPENROUTER_API_KEY.ifBlank { null }
    const val REQUEST_TIMEOUT_SECONDS = 20L
}
