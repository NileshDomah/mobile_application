package com.king.mob_app_assignment.util

import com.king.mob_app_assignment.data.repository.AuthRepository
import com.king.mob_app_assignment.data.repository.SupportRepository
import com.king.mob_app_assignment.domain.model.SupportTurnSender
import com.king.mob_app_assignment.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires a local notification on an admin's own device when their live support-inbox listener
 * sees a genuinely new OPEN message arrive. Started once from MobAppAssignment.onCreate() and
 * runs for the lifetime of the process - same architecture as AiReviewCoordinator's app-scoped
 * background collector.
 *
 * This is NOT server-triggered push (no Cloud Function/FCM - see PLACEHOLDERS_TODO.csv for why:
 * the project stayed off the paid Blaze plan by choice). It only fires while this specific
 * admin's own device has the app process alive and signed in as that admin; it will not wake a
 * killed app or notify a different admin's device. That is a real, documented limitation, not a
 * claim of true push delivery - see REPORT_NOTES.md.
 */
@Singleton
class AdminSupportNotifier @Inject constructor(
    private val authRepository: AuthRepository,
    private val supportRepository: SupportRepository,
    private val notificationHelper: NotificationHelper,
) {

    // Guards against replaying the entire existing inbox as notifications the moment an admin
    // signs in - only turns whose own lastMessageAt is after this notifier's start time are ever
    // notified. Set once, the first time the collector actually starts observing (not at object
    // construction, since Hilt could construct this well before the admin is confirmed signed
    // in). Keyed by "ticketId@lastMessageAt" rather than just ticketId, since a citizen can send
    // several separate turns to the same ticket over time and each one is its own notification-
    // worthy event, not just the ticket's first message.
    private var startedAtMillis: Long? = null
    private val notifiedTurnKeys = mutableSetOf<String>()

    fun start(appScope: CoroutineScope) {
        appScope.launch {
            authRepository.currentUser
                .distinctUntilChangedBy { it?.uid }
                .flatMapLatest { user ->
                    if (user?.role == UserRole.ADMIN) {
                        if (startedAtMillis == null) startedAtMillis = System.currentTimeMillis()
                        supportRepository.getAllTickets()
                    } else {
                        flowOf(emptyList())
                    }
                }
                .catch { emit(emptyList()) }
                .collect { tickets ->
                    val since = startedAtMillis ?: return@collect
                    tickets
                        .filter { it.lastMessageSender == SupportTurnSender.CITIZEN }
                        .filter { it.lastMessageAt > since }
                        .filter { "${it.id}@${it.lastMessageAt}" !in notifiedTurnKeys }
                        .forEach { ticket ->
                            notifiedTurnKeys += "${ticket.id}@${ticket.lastMessageAt}"
                            notificationHelper.notifyNewSupportMessage(
                                messageId = ticket.id,
                                userDisplayName = ticket.userDisplayName,
                                message = ticket.lastMessagePreview,
                            )
                        }
                }
        }
    }
}
