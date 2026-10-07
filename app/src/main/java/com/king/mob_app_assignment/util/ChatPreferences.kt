package com.king.mob_app_assignment.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.king.mob_app_assignment.ai.ChatMessage
import com.king.mob_app_assignment.ai.ChatRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatDataStore by preferencesDataStore(name = "chat_settings")

/**
 * Persists the assistant chat conversation across the ChatViewModel's own lifecycle -
 * user-reported: "if I exit the app and re-enter the chatbot all chat disappears, it should
 * save." ChatViewModel previously kept the whole conversation purely in memory
 * (uiState.messages + its own private conversationHistory sent to the LLM), which vanished the
 * instant its nav back-stack entry was popped (ChatScreen's own onBackClick) or the process died,
 * with no persistence layer underneath at all.
 *
 * Stores the FULL conversation (including the system prompt, so a restored session can resume
 * sending more turns to the LLM without rebuilding the system prompt or losing the model's own
 * context) as one JSON-encoded string, keyed by uid so switching accounts never shows one user's
 * conversation to another. A single flat conversation, not a list of separate chat sessions -
 * matches the explicit request's own framing ("create new chat" replaces the current one, it
 * doesn't imply multiple saved conversations to switch between).
 */
@Singleton
class ChatPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun key(uid: String) = stringPreferencesKey("chat_history_$uid")

    fun conversationFlow(uid: String): Flow<List<ChatMessage>> = context.chatDataStore.data.map { prefs ->
        prefs[key(uid)]?.let { decodeConversation(it) } ?: emptyList()
    }

    suspend fun getConversation(uid: String): List<ChatMessage> = conversationFlow(uid).first()

    suspend fun saveConversation(uid: String, messages: List<ChatMessage>) {
        context.chatDataStore.edit { prefs -> prefs[key(uid)] = encodeConversation(messages) }
    }

    /** Called by the explicit "New chat" action - see ChatViewModel.startNewChat(). */
    suspend fun clearConversation(uid: String) {
        context.chatDataStore.edit { prefs -> prefs.remove(key(uid)) }
    }

    private fun encodeConversation(messages: List<ChatMessage>): String {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("role", message.role.name)
                    put("content", message.content)
                },
            )
        }
        return array.toString()
    }

    private fun decodeConversation(raw: String): List<ChatMessage> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val role = ChatRole.entries.firstOrNull { it.name == obj.optString("role") } ?: return@mapNotNull null
            ChatMessage(role = role, content = obj.optString("content"))
        }
    }.getOrDefault(emptyList())
}
