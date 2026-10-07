package com.king.mob_app_assignment.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.king.mob_app_assignment.data.remote.FirestoreConstants
import com.king.mob_app_assignment.domain.model.User
import com.king.mob_app_assignment.domain.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                trySend(firebaseUser.toBasicUser())
                fetchUserDocument(firebaseUser.uid) { fetched ->
                    trySend(fetched ?: firebaseUser.toBasicUser())
                }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun fetchUserDocument(uid: String, onResult: (User?) -> Unit) {
        firestore.collection(FirestoreConstants.USERS_COLLECTION).document(uid)
            .get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.toUserOrNull()) }
            .addOnFailureListener { onResult(null) }
    }

    override suspend fun register(email: String, password: String, displayName: String, username: String, phoneNumber: String): Result<User> =
        runCatching {
            val normalizedUsername = username.trim().lowercase()
            val normalizedEmail = email.trim().lowercase()
            require(normalizedUsername.isNotBlank()) { "Username is required" }

            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: error("Registration failed: no user returned")

            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val newUser = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName,
                username = normalizedUsername,
                phoneNumber = phoneNumber.trim(),
                role = UserRole.CITIZEN,
            )

            // Reserving the username/email and writing the user doc inside one transaction closes
            // the race window between isUsernameTaken()'s earlier check (used for live UI
            // feedback) and the actual write - two registrations racing on the same username
            // can't both succeed, since Firestore transactions abort-and-retry on a conflicting
            // concurrent write to any document they've read. If the username was taken in the
            // meantime (e.g. by a concurrent registration), this throws and the whole
            // registration reports failure - the already-created FirebaseAuth account is left
            // as-is rather than attempting a compensating delete, a known gap (see
            // PLACEHOLDERS_TODO.csv). The /emails/{normalizedEmail} reservation doc mirrors
            // /usernames/{username} exactly, added so resolveEmailToUsername() (the "forgot
            // username" flow) can look up a single doc by email before the caller is signed in -
            // only ever populated for accounts registered after this doc was introduced (see
            // PLACEHOLDERS_TODO.csv for the pre-existing-account gap).
            firestore.runTransaction { transaction ->
                val usernameDocRef = firestore.collection(FirestoreConstants.USERNAMES_COLLECTION)
                    .document(normalizedUsername)
                val existing = transaction.get(usernameDocRef)
                if (existing.exists()) {
                    error("That username is already taken")
                }
                transaction.set(usernameDocRef, mapOf(FirestoreConstants.UsernameFields.UID to firebaseUser.uid))
                transaction.set(
                    firestore.collection(FirestoreConstants.EMAILS_COLLECTION).document(normalizedEmail),
                    mapOf(FirestoreConstants.EmailFields.UID to firebaseUser.uid),
                )
                // Minimal pre-auth-readable lookup doc (email + username ONLY, never role or
                // displayName) - resolveUsernameToEmail()/resolveEmailToUsername() below read
                // this instead of the full /users/{uid} document, so a pre-auth caller who
                // already has/guesses a uid learns only these two non-sensitive fields, not an
                // entire profile. See FirestoreConstants.LOGIN_LOOKUP_COLLECTION's doc.
                transaction.set(
                    firestore.collection(FirestoreConstants.LOGIN_LOOKUP_COLLECTION).document(firebaseUser.uid),
                    mapOf(
                        FirestoreConstants.LoginLookupFields.EMAIL to normalizedEmail,
                        FirestoreConstants.LoginLookupFields.USERNAME to normalizedUsername,
                    ),
                )
                transaction.set(
                    firestore.collection(FirestoreConstants.USERS_COLLECTION).document(firebaseUser.uid),
                    newUser.toFirestoreMap(),
                )
            }.await()

            newUser
        }

    override suspend fun isUsernameTaken(username: String): Boolean {
        val normalized = username.trim().lowercase()
        if (normalized.isBlank()) return false
        return runCatching {
            firestore.collection(FirestoreConstants.USERNAMES_COLLECTION)
                .document(normalized)
                .get()
                .await()
                .exists()
        }.getOrDefault(false)
    }

    override suspend fun resolveUsernameToEmail(username: String): String? {
        val normalized = username.trim().lowercase()
        if (normalized.isBlank()) return null
        return runCatching {
            val usernameSnapshot = firestore.collection(FirestoreConstants.USERNAMES_COLLECTION)
                .document(normalized)
                .get()
                .await()
            val uid = usernameSnapshot.getString(FirestoreConstants.UsernameFields.UID) ?: return null
            // Reads the narrow /loginLookup/{uid} doc (email + username only), not the full
            // /users/{uid} profile - see FirestoreConstants.LOGIN_LOOKUP_COLLECTION's doc for why.
            firestore.collection(FirestoreConstants.LOGIN_LOOKUP_COLLECTION)
                .document(uid)
                .get()
                .await()
                .getString(FirestoreConstants.LoginLookupFields.EMAIL)
        }.getOrNull()
    }

    override suspend fun resolveEmailToUsername(email: String): String? {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return null
        return runCatching {
            val emailSnapshot = firestore.collection(FirestoreConstants.EMAILS_COLLECTION)
                .document(normalized)
                .get()
                .await()
            val uid = emailSnapshot.getString(FirestoreConstants.EmailFields.UID) ?: return null
            firestore.collection(FirestoreConstants.LOGIN_LOOKUP_COLLECTION)
                .document(uid)
                .get()
                .await()
                .getString(FirestoreConstants.LoginLookupFields.USERNAME)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        firebaseAuth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun login(email: String, password: String): Result<User> =
        runCatching {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: error("Login failed: no user returned")
            val snapshot = firestore.collection(FirestoreConstants.USERS_COLLECTION)
                .document(firebaseUser.uid)
                .get()
                .await()
            snapshot.toUserOrNull() ?: firebaseUser.toBasicUser()
        }

    override fun logout() {
        firebaseAuth.signOut()
    }

    override suspend fun fetchCurrentUserProfile(): Result<User?> = runCatching {
        val firebaseUser = firebaseAuth.currentUser ?: return@runCatching null
        val snapshot = firestore.collection(FirestoreConstants.USERS_COLLECTION)
            .document(firebaseUser.uid)
            .get()
            .await()
        snapshot.toUserOrNull() ?: firebaseUser.toBasicUser()
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        val uid = firebaseAuth.currentUser?.uid ?: return@runCatching Unit
        firestore.collection(FirestoreConstants.USERS_COLLECTION)
            .document(uid)
            .update(FirestoreConstants.UserFields.FCM_TOKEN, token)
            .await()
    }

    // Calls the promoteToAdmin Cloud Function - see its own doc comment in functions/src/index.ts
    // for why this has to be a server-side call rather than a direct Firestore write. Returns
    // Result.success(true) if the target was already an admin (idempotent no-op, not an error),
    // Result.success(false) for a genuine promotion. Firebase's own HttpsCallableException carries
    // the function's HttpsError.message through as its own .message, so surfacing that directly
    // (rather than a generic "something went wrong") already gives the caller a real reason
    // ("Only an existing admin can promote another account.", "No account found for that email or
    // username.", etc.) without this client needing to duplicate that logic.
    override suspend fun promoteToAdmin(email: String, username: String): Result<Boolean> = runCatching {
        val data = buildMap<String, Any> {
            if (email.isNotBlank()) put("email", email.trim())
            if (username.isNotBlank()) put("username", username.trim())
        }
        val result = functions.getHttpsCallable("promoteToAdmin").call(data).await()
        val response = result.getData() as? Map<*, *>
        response?.get("alreadyAdmin") == true
    }

    override suspend fun listAllUsers(): Result<List<User>> = runCatching {
        firestore.collection(FirestoreConstants.USERS_COLLECTION)
            .get()
            .await()
            .documents
            .mapNotNull { it.toUserOrNull() }
            .sortedBy { it.displayName.ifBlank { it.username }.lowercase() }
    }

    private fun FirebaseUser.toBasicUser(): User = User(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        role = UserRole.CITIZEN,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserOrNull(): User? {
        if (!exists()) return null
        return User(
            uid = getString(FirestoreConstants.UserFields.UID) ?: id,
            email = getString(FirestoreConstants.UserFields.EMAIL).orEmpty(),
            displayName = getString(FirestoreConstants.UserFields.DISPLAY_NAME).orEmpty(),
            username = getString(FirestoreConstants.UserFields.USERNAME).orEmpty(),
            phoneNumber = getString(FirestoreConstants.UserFields.PHONE_NUMBER).orEmpty(),
            role = UserRole.fromFirestoreValue(getString(FirestoreConstants.UserFields.ROLE)),
            fcmToken = getString(FirestoreConstants.UserFields.FCM_TOKEN).orEmpty(),
        )
    }

    private fun User.toFirestoreMap(): Map<String, Any> = mapOf(
        FirestoreConstants.UserFields.UID to uid,
        FirestoreConstants.UserFields.EMAIL to email,
        FirestoreConstants.UserFields.DISPLAY_NAME to displayName,
        FirestoreConstants.UserFields.USERNAME to username,
        FirestoreConstants.UserFields.PHONE_NUMBER to phoneNumber,
        FirestoreConstants.UserFields.ROLE to role.name,
        FirestoreConstants.UserFields.FCM_TOKEN to fcmToken,
    )
}
