package com.king.mob_app_assignment.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
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

    override suspend fun register(email: String, password: String, displayName: String, username: String): Result<User> =
        runCatching {
            val normalizedUsername = username.trim().lowercase()
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
                role = UserRole.CITIZEN,
            )

            // Reserving the username and writing the user doc inside one transaction closes the
            // race window between isUsernameTaken()'s earlier check (used for live UI feedback)
            // and the actual write - two registrations racing on the same username can't both
            // succeed, since Firestore transactions abort-and-retry on a conflicting concurrent
            // write to any document they've read. If the username was taken in the meantime
            // (e.g. by a concurrent registration), this throws and the whole registration
            // reports failure - the already-created FirebaseAuth account is left as-is rather
            // than attempting a compensating delete, a known gap (see PLACEHOLDERS_TODO.csv).
            firestore.runTransaction { transaction ->
                val usernameDocRef = firestore.collection(FirestoreConstants.USERNAMES_COLLECTION)
                    .document(normalizedUsername)
                val existing = transaction.get(usernameDocRef)
                if (existing.exists()) {
                    error("That username is already taken")
                }
                transaction.set(usernameDocRef, mapOf(FirestoreConstants.UsernameFields.UID to firebaseUser.uid))
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
            firestore.collection(FirestoreConstants.USERS_COLLECTION)
                .document(uid)
                .get()
                .await()
                .getString(FirestoreConstants.UserFields.EMAIL)
        }.getOrNull()
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
            role = UserRole.fromFirestoreValue(getString(FirestoreConstants.UserFields.ROLE)),
        )
    }

    private fun User.toFirestoreMap(): Map<String, Any> = mapOf(
        FirestoreConstants.UserFields.UID to uid,
        FirestoreConstants.UserFields.EMAIL to email,
        FirestoreConstants.UserFields.DISPLAY_NAME to displayName,
        FirestoreConstants.UserFields.USERNAME to username,
        FirestoreConstants.UserFields.ROLE to role.name,
    )
}
