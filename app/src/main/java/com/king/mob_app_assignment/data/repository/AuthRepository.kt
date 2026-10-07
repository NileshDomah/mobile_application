package com.king.mob_app_assignment.data.repository

import com.king.mob_app_assignment.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUser: Flow<User?>

    suspend fun register(email: String, password: String, displayName: String, username: String, phoneNumber: String): Result<User>

    // isUsernameTaken checks the /usernames/{username} reservation doc directly - used for
    // live availability feedback on the register screen, separate from the actual reservation
    // write (which happens transactionally inside register() itself to avoid a race between the
    // check and the real write).
    suspend fun isUsernameTaken(username: String): Boolean

    suspend fun login(email: String, password: String): Result<User>

    // Resolves a username to its account email via the /usernames/{username} reservation doc,
    // so LoginViewModel can accept a username at the login screen and still call login() (above)
    // with a real email underneath - Firebase Auth itself only ever signs in by email/password.
    suspend fun resolveUsernameToEmail(username: String): String?

    // The reverse lookup - "forgot my username" flow. Only ever succeeds for accounts registered
    // after the /emails/{email} reservation doc was introduced; see PLACEHOLDERS_TODO.csv for why
    // accounts registered before that change have no way to recover their username here.
    suspend fun resolveEmailToUsername(email: String): String?

    // Thin wrapper over FirebaseAuth's own built-in password-reset email - deliberately does not
    // reveal whether the email actually has an account (Firebase's own sendPasswordResetEmail
    // already returns success either way in most configurations, to avoid leaking which emails
    // are registered - this wrapper doesn't change that behavior, just exposes it through the
    // same Result-based contract as the rest of this interface).
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    fun logout()

    suspend fun fetchCurrentUserProfile(): Result<User?>

    // Called from FcmTokenService whenever this device's FCM registration token is issued or
    // rotates. Writes only the fcmToken field on the signed-in user's own document - see
    // firestore.rules for why this is scoped to a field-only update rather than a general
    // profile write. A no-op (not an error) if nobody is signed in yet, since the token can
    // arrive before login completes.
    suspend fun updateFcmToken(token: String): Result<Unit>

    // Promotes another account to admin via the promoteToAdmin Cloud Function - the one
    // server-side escape hatch from "role is CITIZEN on create and never self-service" (see
    // firestore.rules' /users/{userId} create rule). Deliberately NOT a direct Firestore write:
    // the function itself re-verifies the CALLER is already an admin before doing anything, using
    // the Admin SDK server-side rather than trusting a Firestore rule that couldn't distinguish
    // "this screen" from "any other write the caller's account is capable of." Exactly one of
    // email/username should be non-blank - whichever the admin typed in.
    suspend fun promoteToAdmin(email: String, username: String): Result<Boolean>

    // Backs PromoteAdminScreen's searchable account picker - admin-only in practice, since
    // firestore.rules' /users/{userId} list rule only grants a full-collection list() to an
    // already-admin caller (a citizen's list attempt is rejected server-side even if this were
    // called from citizen-reachable code). Loads the whole /users collection once; filtering by
    // name/username/email happens client-side in PromoteAdminViewModel rather than as a Firestore
    // query, since this project's user base is coursework-scale (see PLACEHOLDERS_TODO.csv for
    // why this wouldn't scale to a production-sized user table).
    suspend fun listAllUsers(): Result<List<User>>
}
