package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.SupportMessage
import kotlinx.coroutines.flow.Flow

interface SupportRepository {

    suspend fun sendMessage(message: SupportMessage): Result<Unit>

    // Citizen-facing: only the signed-in user's own messages (including any admin reply).
    fun getMessagesForUser(uid: String): Flow<List<SupportMessage>>

    // Admin-facing: every support message across all users.
    fun getAllMessages(): Flow<List<SupportMessage>>

    // Admin-only, enforced by firestore.rules isAdmin() - writes adminReply and flips status.
    suspend fun replyAndResolve(messageId: String, reply: String): Result<Unit>
}
