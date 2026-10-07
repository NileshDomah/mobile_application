import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

// Mirrors FirestoreConstants field/collection names on the Android side
// (data/remote/FirestoreSchema.kt) - kept as plain string literals here since this function has
// no access to the Kotlin source, not because the two are meant to drift independently.
const USERS_COLLECTION = "users";
const REPORTS_COLLECTION = "reports";
const USERNAMES_COLLECTION = "usernames";
const EMAILS_COLLECTION = "emails";
const AUDIT_LOG_COLLECTION = "auditLog";
const REPORT_FIELD_USER_ID = "userId";
const REPORT_FIELD_STATUS = "status";
const REPORT_FIELD_CATEGORY = "category";
const REPORT_FIELD_RESOLUTION_NOTE = "resolutionNote";
const USER_FIELD_FCM_TOKEN = "fcmToken";
const USER_FIELD_ROLE = "role";

/**
 * Fires on every /reports/{reportId} update. Only actually sends a push when the `status`
 * field changed and the report has a real owner (anonymous reports have userId == "" and are
 * skipped - there's nobody to notify). This is the server-side half of the FCM push feature -
 * see FcmTokenService.kt (captures the token) and AuthRepositoryImpl.updateFcmToken()
 * (persists it) on the Android side.
 *
 * Deployed via `firebase deploy --only functions` (requires the project on the Blaze plan -
 * Cloud Functions with a Firestore trigger has no free/Spark-plan option, see REPORT_NOTES.md).
 */
export const sendReportStatusPush = onDocumentUpdated(
  `${REPORTS_COLLECTION}/{reportId}`,
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    const oldStatus = before[REPORT_FIELD_STATUS];
    const newStatus = after[REPORT_FIELD_STATUS];
    if (oldStatus === newStatus) {
      // Most report updates aren't a status change at all (e.g. the AI-review-flag write, or
      // IncidentClusteringWorker attaching incidentId) - only a real status transition is
      // worth interrupting the citizen with a push for.
      return;
    }

    const userId: string = after[REPORT_FIELD_USER_ID] ?? "";
    if (!userId) {
      // Anonymous report - isAnonymous == true reports never carry an owner uid (see
      // firestore.rules' /reports/{reportId} create rule), so there is nobody to push to.
      return;
    }

    const userSnap = await db.collection(USERS_COLLECTION).doc(userId).get();
    const token: string | undefined = userSnap.get(USER_FIELD_FCM_TOKEN);
    if (!token) {
      // No token yet (never launched the app post-install, or notifications never granted) -
      // not an error, just nothing to deliver to.
      logger.info(`No fcmToken for user ${userId}, skipping push for report ${event.params.reportId}`);
      return;
    }

    const category = after[REPORT_FIELD_CATEGORY] ?? "issue";
    const resolutionNote: string = after[REPORT_FIELD_RESOLUTION_NOTE] ?? "";
    const bodyBase = `Your ${category} report is now ${newStatus}`;
    const body = resolutionNote
      ? `${bodyBase}: ${resolutionNote.slice(0, 60)}${resolutionNote.length > 60 ? "..." : ""}`
      : bodyBase;

    try {
      await messaging.send({
        token,
        notification: {
          title: "Report update",
          body,
        },
        // Mirrors NotificationHelper's own channel id so a push and a local-listener
        // notification render identically to the user (see AndroidManifest.xml's
        // default_notification_channel_id meta-data).
        android: {
          notification: {
            channelId: "report_status_updates",
          },
        },
        data: {
          reportId: event.params.reportId,
        },
      });
    } catch (error) {
      // A stale/unregistered token is the common failure mode here (uninstalled app, token
      // rotated without the write landing yet) - logging and returning rather than throwing
      // keeps this from being retried forever by Cloud Functions' at-least-once delivery.
      logger.warn(`Failed to send push to user ${userId} for report ${event.params.reportId}`, error);
    }
  },
);

interface PromoteToAdminRequest {
  // Exactly one of these must be provided - whichever the admin typed into the promote-account
  // screen. Resolved to a uid the same way login itself resolves a username to an email: via the
  // existing /usernames and /emails reservation-doc collections, never a full collection query.
  email?: string;
  username?: string;
}

/**
 * Callable Cloud Function letting an existing admin promote another account to admin, without
 * ever touching the Firebase console. This is the one server-side escape hatch from the app's
 * otherwise-absolute "role is set to CITIZEN on create and never self-service from there" rule
 * (see firestore.rules' /users/{userId} create rule, and AuthRepositoryImpl.register()) - the
 * Admin SDK used here runs with full privileges and bypasses Firestore security rules entirely,
 * which is exactly why this had to be a Cloud Function rather than a client-side write: a rule
 * change permissive enough to let an admin write role:'ADMIN' on another user's document would
 * let ANY means of authenticating as that admin (not just this screen) do the same thing, since
 * rules can't distinguish "the intended button" from "any other write the account is capable of."
 * Here, only this specific function's logic runs with elevated privilege - the client itself
 * still has no more Firestore access than before.
 */
export const promoteToAdmin = onCall<PromoteToAdminRequest>(async (request) => {
  const callerUid = request.auth?.uid;
  if (!callerUid) {
    throw new HttpsError("unauthenticated", "You must be signed in to do this.");
  }

  const callerSnap = await db.collection(USERS_COLLECTION).doc(callerUid).get();
  if (callerSnap.get(USER_FIELD_ROLE) !== "ADMIN") {
    // Deliberately generic - a non-admin caller learns only "not permitted," never anything
    // about whether the target account exists or what its current role is.
    throw new HttpsError("permission-denied", "Only an existing admin can promote another account.");
  }

  const email = request.data.email?.trim().toLowerCase();
  const username = request.data.username?.trim().toLowerCase();
  if (!email && !username) {
    throw new HttpsError("invalid-argument", "Provide the target account's email or username.");
  }

  let targetUid: string | undefined;
  if (username) {
    const usernameSnap = await db.collection(USERNAMES_COLLECTION).doc(username).get();
    targetUid = usernameSnap.get("uid");
  } else if (email) {
    const emailSnap = await db.collection(EMAILS_COLLECTION).doc(email).get();
    targetUid = emailSnap.get("uid");
  }

  if (!targetUid) {
    throw new HttpsError("not-found", "No account found for that email or username.");
  }

  const targetRef = db.collection(USERS_COLLECTION).doc(targetUid);
  const targetSnap = await targetRef.get();
  if (!targetSnap.exists) {
    throw new HttpsError("not-found", "No account found for that email or username.");
  }
  const previousRole = targetSnap.get(USER_FIELD_ROLE) ?? "CITIZEN";
  if (previousRole === "ADMIN") {
    // Not an error - idempotent no-op so re-submitting the same promotion (e.g. a flaky network
    // retry) doesn't need special client-side handling, and still records nothing misleading in
    // the audit trail (no real change happened).
    return { alreadyAdmin: true };
  }

  await targetRef.update({ [USER_FIELD_ROLE]: "ADMIN" });

  // Same shared audit-log collection every other admin action (status changes, incident
  // validation, work orders) already writes to - see AuditHistoryComponents.kt on the Android
  // side, which reads this collection back for display.
  await db.collection(AUDIT_LOG_COLLECTION).add({
    entityType: "user",
    entityId: targetUid,
    action: "Promoted to admin",
    performedByUid: callerUid,
    timestamp: FieldValue.serverTimestamp(),
    previousValue: previousRole,
    newValue: "ADMIN",
    note: "",
  });

  return { alreadyAdmin: false };
});
